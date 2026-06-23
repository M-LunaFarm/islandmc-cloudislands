package kr.lunaf.cloudislands.coreservice.config;

public record MigrationConfig(
    String policy,
    boolean superiorSkyblock2Enabled
) {}
