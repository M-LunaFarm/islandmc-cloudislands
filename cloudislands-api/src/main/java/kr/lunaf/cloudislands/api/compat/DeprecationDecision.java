package kr.lunaf.cloudislands.api.compat;

public record DeprecationDecision(
    boolean removable,
    String deprecatedInVersion,
    String removalVersion,
    String reason
) {}
