package kr.lunaf.cloudislands.coreservice.config;

public record DatabaseConfig(
    String repositoryMode,
    String jdbcUrl,
    String configuredDatabaseType,
    String username,
    String password,
    int poolSize,
    boolean autoSchema,
    boolean fallbackEnabled,
    String fallbackOrder,
    boolean fallbackRequireSharedBeforeLocal,
    boolean fallbackLocalLast,
    String fallbackProductionSafeOrder,
    boolean allowInMemoryFallback,
    String coreApiBaseUrl,
    boolean coreApiAuthTokenConfigured,
    boolean coreApiAdminTokenConfigured,
    int coreApiTimeoutMillis
) {}
