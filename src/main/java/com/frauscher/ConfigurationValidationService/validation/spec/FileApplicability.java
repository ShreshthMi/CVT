package com.frauscher.ConfigurationValidationService.validation.spec;

import com.frauscher.ConfigurationValidationService.model.ParsedConfigFile;

public enum FileApplicability {

    ALL {
        @Override
        public boolean applies(ParsedConfigFile file) {
            return true;
        }
    },
    TRACKSECTIONDETAILS {
        @Override
        public boolean applies(ParsedConfigFile file) {
            return file.isTrackSectionDetails();
        }
    },
    ACOIOEXBDETAILS {
        @Override
        public boolean applies(ParsedConfigFile file) {
            return file.isAcoIoexbDetails();
        }
    },
    DTIOEXBDETAILS {
        @Override
        public boolean applies(ParsedConfigFile file) {
            return file.isDtIoexbDetails();
        }
    },
    COMDETAILS {
        @Override
        public boolean applies(ParsedConfigFile file) {
            return file.isComDetails();
        }
    };

    public abstract boolean applies(ParsedConfigFile file);

    public static FileApplicability from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return FileApplicability.valueOf(value.trim().toUpperCase());
    }
}
