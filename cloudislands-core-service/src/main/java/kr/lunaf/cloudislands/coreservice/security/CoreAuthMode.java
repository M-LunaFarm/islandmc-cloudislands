package kr.lunaf.cloudislands.coreservice.security;

import java.util.Locale;

public enum CoreAuthMode {
    MTLS_REQUIRED,
    TOKEN_REQUIRED,
    MTLS_OR_TOKEN;

    public static CoreAuthMode fromConfig(String value, boolean legacyRequireMtls) {
        if (value == null || value.isBlank()) {
            return legacyRequireMtls ? MTLS_REQUIRED : TOKEN_REQUIRED;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return CoreAuthMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported Core auth mode " + value + "; expected MTLS_REQUIRED, TOKEN_REQUIRED, or MTLS_OR_TOKEN");
        }
    }

    public boolean acceptsToken() {
        return this == TOKEN_REQUIRED || this == MTLS_OR_TOKEN;
    }

    public boolean acceptsMtls() {
        return this == MTLS_REQUIRED || this == MTLS_OR_TOKEN;
    }
}
