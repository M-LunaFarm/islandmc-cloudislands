package kr.lunaf.cloudislands.coreservice.template;

public record IslandTemplateSnapshot(
    String id,
    String displayName,
    boolean enabled,
    String minNodeVersion
) {}
