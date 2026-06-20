package kr.lunaf.cloudislands.common.config;

public record ConfigSource(String name, int precedence, String yaml) {
    public ConfigSource {
        name = name == null || name.isBlank() ? "unnamed" : name;
        yaml = yaml == null ? "" : yaml;
    }
}
