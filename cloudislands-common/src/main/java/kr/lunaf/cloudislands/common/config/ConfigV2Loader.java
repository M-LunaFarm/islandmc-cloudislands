package kr.lunaf.cloudislands.common.config;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

public final class ConfigV2Loader {
    private ConfigV2Loader() {
    }

    public static ConfigSnapshot load(List<ConfigSource> sources) {
        return load(sources, Clock.systemUTC());
    }

    public static ConfigSnapshot load(List<ConfigSource> sources, Clock clock) {
        List<ConfigSource> ordered = sources == null ? List.of() : sources.stream()
            .sorted(Comparator.comparingInt(ConfigSource::precedence).thenComparing(ConfigSource::name))
            .toList();
        String effective = ordered.stream()
            .map(ConfigSource::yaml)
            .filter(yaml -> !yaml.isBlank())
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("");
        ConfigValidationResult validation = ConfigV2Validator.validateYaml("effective-config", effective);
        return new ConfigSnapshot(ordered, effective, validation, clock == null ? Clock.systemUTC().instant() : clock.instant());
    }
}
