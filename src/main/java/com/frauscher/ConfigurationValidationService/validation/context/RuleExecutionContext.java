package com.frauscher.ConfigurationValidationService.validation.context;
import com.frauscher.ConfigurationValidationService.model.RuleConfig;
import com.frauscher.ConfigurationValidationService.validation.payload.ResolvedPayload;

public class RuleExecutionContext {

    private final FileContext fileContext;
    private final ValidationKey key;
    private final RuleConfig rule;
    private final ResolvedPayload payload;
    private final ResolvedPayloadContext resolvedPayloadContext;
    private final DuplicateValueRegistry duplicateRegistry;

    public RuleExecutionContext(
            FileContext fileContext,
            ValidationKey key,
            RuleConfig rule,
            ResolvedPayload payload,
            ResolvedPayloadContext resolvedPayloadContext,
            DuplicateValueRegistry duplicateRegistry) {



        this.fileContext = fileContext;
        this.key = key;
        this.rule = rule;
        this.payload = payload;
        this.resolvedPayloadContext = resolvedPayloadContext;
        this.duplicateRegistry = duplicateRegistry;
    }

    public FileContext fileContext() {
        return fileContext;
    }

    public ValidationKey key() {
        return key;
    }

    public RuleConfig rule() {
        return rule;
    }

    public ResolvedPayload payload() {
        return payload;
    }

    public ResolvedPayloadContext resolvedPayloadContext() {
        return resolvedPayloadContext;
    }

    public DuplicateValueRegistry duplicateRegistry() {
        return duplicateRegistry;
    }
}

