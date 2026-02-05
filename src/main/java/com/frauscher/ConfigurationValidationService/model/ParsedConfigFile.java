package com.frauscher.ConfigurationValidationService.model;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParsedConfigFile {

    private String fileName;
    private List<ConfigBlock> blocks;
    private boolean trackSectionDetails;
    private boolean ioexbDetails;
    private boolean comDetails;
    private int id;
}