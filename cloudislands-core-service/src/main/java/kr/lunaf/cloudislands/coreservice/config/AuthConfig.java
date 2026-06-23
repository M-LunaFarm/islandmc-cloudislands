package kr.lunaf.cloudislands.coreservice.config;

public record AuthConfig(
    String coreToken,
    String nodeCredentials,
    String adminToken,
    String adminPermissions,
    String ipAllowlist,
    boolean adminApiEnabled,
    boolean publicAdminApiEnabled,
    boolean requireMtls,
    String mtlsVerifiedHeader,
    String mtlsVerifiedValue,
    String mtlsTrustedProxies
) {}
