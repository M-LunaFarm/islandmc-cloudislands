package kr.lunaf.cloudislands.common.config;

import java.time.Instant;
import java.util.List;

public record ConfigSnapshot(
    List<ConfigSource> sources,
    String effectiveYaml,
    ConfigValidationResult validation,
    Instant createdAt
) {
    public ConfigSnapshot {
        sources = sources == null ? List.of() : List.copyOf(sources);
        effectiveYaml = effectiveYaml == null ? "" : effectiveYaml;
        validation = validation == null ? new ConfigValidationResult(List.of()) : validation;
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
    }

    public boolean valid() {
        return validation.valid();
    }

    public String redactedEffectiveYaml() {
        return ConfigV2Validator.redactYaml(effectiveYaml);
    }
}
