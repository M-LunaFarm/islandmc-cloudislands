package kr.lunaf.cloudislands.coreservice.config;

public record CoreConfigSections(
    ServerConfig server,
    AuthConfig auth,
    DatabaseConfig database,
    RedisConfig redis,
    StorageConfig storage,
    RoutingConfig routing,
    JobConfig job,
    SnapshotConfig snapshot,
    ObservabilityConfig observability,
    MigrationConfig migration
) {}
