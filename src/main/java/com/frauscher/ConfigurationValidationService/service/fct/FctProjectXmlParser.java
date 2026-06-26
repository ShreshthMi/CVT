package com.frauscher.ConfigurationValidationService.service.fct;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.frauscher.ConfigurationValidationService.dto.fct.AcoIoExb;
import com.frauscher.ConfigurationValidationService.dto.fct.Chain;
import com.frauscher.ConfigurationValidationService.dto.fct.ComAebMap;
import com.frauscher.ConfigurationValidationService.dto.fct.EvaluatedFma;
import com.frauscher.ConfigurationValidationService.dto.fct.FctAeb;
import com.frauscher.ConfigurationValidationService.dto.fct.FctCom;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidException;
import com.frauscher.ConfigurationValidationService.exception.FctInvalidReason;

/**
 * Parses the FCT2 {@code Project.xml} into a {@link ComAebMap} (design §5.3–§5.6): groups {@code Bp}s
 * into CAN segments via {@code CanConnections}, collapses MASTER/SLAVE redundancy, and per AEB
 * surfaces its FMAs, ACO IoExbs (with OutputFma cross-resolution), and DT-IoExb count. ADC files are
 * not touched — this reads the FCT baseline only.
 */
@Component
public class FctProjectXmlParser {

    private record FmaRef(String name, String fmaId, String aebInternId) {
    }

    public ComAebMap parse(InputStream projectXml) {
        Document doc = readSecureXml(projectXml);

        Map<String, String> dpIdByAebInternId = new HashMap<>();
        Map<String, FmaRef> fmaByInternId = new HashMap<>();
        index(doc, dpIdByAebInternId, fmaByInternId);

        List<Element> bps = elements(doc.getElementsByTagName("Bp"));
        List<List<Element>> segments = groupIntoSegments(bps);

        List<Chain> chains = new ArrayList<>();
        Set<String> seenDpIds = new HashSet<>();
        for (List<Element> segment : segments) {
            Chain chain = buildChain(segment, fmaByInternId, dpIdByAebInternId, seenDpIds);
            if (chain != null) {
                chains.add(chain);
            }
        }
        return new ComAebMap(chains);
    }

    private void index(Document doc, Map<String, String> dpIdByAebInternId, Map<String, FmaRef> fmaByInternId) {
        for (Element aeb : elements(doc.getElementsByTagName("Aeb"))) {
            dpIdByAebInternId.put(aeb.getAttribute("internId"), childText(aeb, "Id"));
        }
        for (Element fma : elements(doc.getElementsByTagName("Fma"))) {
            fmaByInternId.put(fma.getAttribute("internId"),
                    new FmaRef(fma.getAttribute("name").strip(), fma.getAttribute("fmaId"), fma.getAttribute("aebInternId")));
        }
    }

    private List<List<Element>> groupIntoSegments(List<Element> bps) {
        Map<String, Element> bpById = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Element bp : bps) {
            String id = bp.getAttribute("internId");
            bpById.put(id, bp);
            adjacency.computeIfAbsent(id, k -> new HashSet<>());
        }
        for (Element bp : bps) {
            String id = bp.getAttribute("internId");
            Element can = firstChild(firstChild(bp, "Parameter"), "CanConnections");
            if (can == null) {
                continue;
            }
            for (String attr : new String[] {"next", "previous"}) {
                String other = can.getAttribute(attr);
                if (other == null || other.isEmpty()) {
                    continue;
                }
                if (!bpById.containsKey(other)) {
                    throw new FctInvalidException(FctInvalidReason.DANGLING_CAN_CONNECTION,
                            "Bp " + id + " CanConnections " + attr + " points at unknown Bp " + other);
                }
                adjacency.get(id).add(other);
                adjacency.get(other).add(id);
            }
        }

        List<List<Element>> segments = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String id : bpById.keySet()) {
            if (visited.contains(id)) {
                continue;
            }
            List<Element> segment = new ArrayList<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(id);
            visited.add(id);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                segment.add(bpById.get(current));
                for (String neighbour : adjacency.get(current)) {
                    if (visited.add(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
            segments.add(segment);
        }
        return segments;
    }

    private Chain buildChain(List<Element> segment, Map<String, FmaRef> fmas,
                             Map<String, String> dpIdByAeb, Set<String> seenDpIds) {
        List<Element> coms = new ArrayList<>();
        List<Element> aebs = new ArrayList<>();
        List<Element> ioExbs = new ArrayList<>();
        for (Element bp : segment) {
            for (Element child : childElements(firstChild(bp, "Children"))) {
                switch (child.getTagName()) {
                    case "Com" -> coms.add(child);
                    case "Aeb" -> aebs.add(child);
                    case "IoExb" -> ioExbs.add(child);
                    default -> { /* Psc, BlankingPlate, EmptySlot, ... ignored */ }
                }
            }
        }

        if (coms.isEmpty() && aebs.isEmpty()) {
            return null; // power-only / blank backplane (e.g. BP_PWR with only a PSC) — not a CAN segment
        }
        ComResult com = resolveCom(coms);

        Map<String, List<Element>> acoByRefAeb = new HashMap<>();
        Map<String, Integer> dtCountByRefAeb = new HashMap<>();
        for (Element ioExb : ioExbs) {
            classifyIoExb(ioExb, acoByRefAeb, dtCountByRefAeb);
        }

        List<FctAeb> aebResults = new ArrayList<>();
        for (Element aeb : aebs) {
            String internId = aeb.getAttribute("internId");
            String dpId = childText(aeb, "Id");
            if (!seenDpIds.add(dpId)) {
                throw new FctInvalidException(FctInvalidReason.DUPLICATE_ENTITY_ID, "duplicate AEB Id " + dpId);
            }
            List<AcoIoExb> acoIoExbs = new ArrayList<>();
            for (Element ioExb : acoByRefAeb.getOrDefault(internId, List.of())) {
                acoIoExbs.add(toAcoIoExb(ioExb, fmas, dpIdByAeb));
            }
            aebResults.add(new FctAeb(
                    dpId,
                    aeb.getAttribute("cpName").strip(),
                    evaluatedFmas(aeb, dpId),
                    acoIoExbs,
                    dtCountByRefAeb.getOrDefault(internId, 0)));
        }
        return new Chain(com.com(), com.redundant(), aebResults);
    }

    private record ComResult(FctCom com, boolean redundant) {
    }

    private ComResult resolveCom(List<Element> coms) {
        if (coms.isEmpty()) {
            throw new FctInvalidException(FctInvalidReason.CHAIN_NO_COM, "CAN segment has no COM");
        }
        if (coms.size() == 1) {
            return new ComResult(toCom(coms.get(0)), false);
        }
        if (coms.size() == 2) {
            Element master = coms.stream().filter(c -> "MASTER".equalsIgnoreCase(childText(c, "ComMode"))).findFirst().orElse(null);
            Element slave = coms.stream().filter(c -> "SLAVE".equalsIgnoreCase(childText(c, "ComMode"))).findFirst().orElse(null);
            if (master == null || slave == null) {
                throw new FctInvalidException(FctInvalidReason.MULTI_COM_NO_REDUNDANCY,
                        "CAN segment has 2 COMs that are not a MASTER/SLAVE pair");
            }
            return new ComResult(toCom(master), true);
        }
        throw new FctInvalidException(FctInvalidReason.MULTI_COM_NO_REDUNDANCY,
                "CAN segment has " + coms.size() + " COMs");
    }

    private void classifyIoExb(Element ioExb, Map<String, List<Element>> acoByRefAeb, Map<String, Integer> dtCountByRefAeb) {
        String refAeb = childText(ioExb, "RefAeb");
        if (firstChild(ioExb, "AxleCountingOutput") != null) {
            acoByRefAeb.computeIfAbsent(refAeb, k -> new ArrayList<>()).add(ioExb);
        } else if (firstChild(ioExb, "DataTransmission") != null) {
            dtCountByRefAeb.merge(refAeb, 1, Integer::sum);
        } else {
            throw new FctInvalidException(FctInvalidReason.UNSUPPORTED_IOEXB_MODE,
                    "IoExb " + ioExb.getAttribute("internId") + " is neither ACO nor DT mode");
        }
    }

    private List<EvaluatedFma> evaluatedFmas(Element aeb, String dpId) {
        List<EvaluatedFma> out = new ArrayList<>();
        Element fmas = firstChild(aeb, "Fmas");
        if (fmas != null) {
            for (Element fma : childElements(fmas)) {
                if ("Fma".equals(fma.getTagName())) {
                    out.add(new EvaluatedFma(fma.getAttribute("name").strip(), fma.getAttribute("fmaId"), dpId));
                }
            }
        }
        return out;
    }

    private AcoIoExb toAcoIoExb(Element ioExb, Map<String, FmaRef> fmas, Map<String, String> dpIdByAeb) {
        Element aco = firstChild(ioExb, "AxleCountingOutput");
        String[] one = resolveOutputFma(firstChild(aco, "OutputFma1"), fmas, dpIdByAeb);
        String[] two = resolveOutputFma(firstChild(aco, "OutputFma2"), fmas, dpIdByAeb);
        return new AcoIoExb(
                ioExb.getAttribute("name").strip(),
                one[0], one[1], one[2],
                two == null ? null : two[0], two == null ? null : two[1], two == null ? null : two[2]);
    }

    /** Resolves an OutputFma element's text (an FMA internId) to {name, fmaId, parent-AEB dpId}, or null if absent. */
    private String[] resolveOutputFma(Element outputFma, Map<String, FmaRef> fmas, Map<String, String> dpIdByAeb) {
        if (outputFma == null) {
            return null;
        }
        String fmaInternId = outputFma.getTextContent().strip();
        FmaRef ref = fmas.get(fmaInternId);
        if (ref == null) {
            throw new FctInvalidException(FctInvalidReason.XREF_UNRESOLVED, "OutputFma -> unknown FMA internId " + fmaInternId);
        }
        String dpId = dpIdByAeb.get(ref.aebInternId());
        if (dpId == null) {
            throw new FctInvalidException(FctInvalidReason.XREF_UNRESOLVED,
                    "FMA " + fmaInternId + " aebInternId " + ref.aebInternId() + " has no AEB");
        }
        return new String[] {ref.name(), ref.fmaId(), dpId};
    }

    private FctCom toCom(Element com) {
        return new FctCom(childText(com, "Id"), com.getAttribute("name").strip());
    }

    private Document readSecureXml(InputStream in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            return dbf.newDocumentBuilder().parse(in);
        } catch (FctInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new FctInvalidException(FctInvalidReason.XML_PARSE_FAILED, "Project.xml parse failed: " + e.getMessage());
        }
    }

    // ---- DOM helpers (direct children only) ----

    private Element firstChild(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        for (Element child : childElements(parent)) {
            if (tag.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private String childText(Element parent, String tag) {
        Element child = firstChild(parent, tag);
        return (child == null) ? "" : child.getTextContent().strip();
    }

    private List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) node);
            }
        }
        return out;
    }

    private List<Element> elements(NodeList nodes) {
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }
}