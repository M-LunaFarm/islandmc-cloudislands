plugins { application }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-storage"))
    implementation(project(":cloudislands-migration"))
    testImplementation(libs.junit.jupiter)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.client)
}

application {
    mainClass.set("kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication")
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Core-Setup-Database-Path" to "setup.database",
            "CloudIslands-Core-Setup-Database-Supported-Targets" to "POSTGRESQL,MYSQL,MARIADB,CORE_API",
            "CloudIslands-Core-JDBC-Native-Backend" to "POSTGRESQL,MYSQL,MARIADB",
            "CloudIslands-Core-JDBC-Drivers" to "org.postgresql:postgresql,com.mysql:mysql-connector-j,org.mariadb.jdbc:mariadb-java-client",
            "CloudIslands-Core-JDBC-Auto-Schema-Path" to "setup.database.auto-schema",
            "CloudIslands-Core-JDBC-Auto-Schema-Policy" to "explicit-opt-in-postgresql-mysql-mariadb-bootstrap",
            "CloudIslands-Core-JDBC-Auto-Schema-Resource" to "postgresql=/db/migration/V1..V54,mysql-mariadb=/db/mysql/V1__cloudislands_mysql_schema.sql",
            "CloudIslands-Core-JDBC-Auto-Schema-History-Table" to "cloudislands_schema_bootstrap",
            "CloudIslands-Core-JDBC-Auto-Schema-Retry-Policy" to "ignore-existing-schema-objects-and-mark-bootstrap-after-complete-apply",
            "CloudIslands-Core-JDBC-Auto-Schema-Guard-Policy" to "generated-columns-enforce-active-unique-guards-for-mysql-mariadb",
            "CloudIslands-Core-JDBC-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC",
            "CloudIslands-Core-Unsupported-JDBC-Policy" to "safe-fallback-with-shared-jdbc-core-api-or-in-memory",
            "CloudIslands-Core-Setup-Fallback-Policy" to "unsupported-or-incomplete-selection-falls-through-configured-order",
            "CloudIslands-Core-Setup-Core-API-Policy" to "client-addon-state-marker-core-self-storage-requires-durable-jdbc-or-safe-in-memory-fallback",
            "CloudIslands-Core-Setup-Fallback-Safety" to "disabled-fallback-still-refuses-unsafe-unsupported-jdbc",
            "CloudIslands-Core-Config-Surface" to "server,database,redis,storage,routing,security,upgrades,block-values",
            "CloudIslands-Core-Addon-State-Bulk-Endpoints" to "/v1/addons/state/table/bulk,/v1/addons/state/table-key-value/bulk-save,/v1/addons/state/table/key-value/bulk-save,/v1/addons/state/table/key-value/bulk/save,/v1/addons/state/table/key-value/bulk,/v1/addons/state/table/key-value/bulk-load,/v1/addons/state/table/load,/v1/addons/state/table/bulk-set",
            "CloudIslands-Core-Addon-Island-State-Bulk-Endpoints" to "/v1/addons/islands/state/table/bulk,/v1/addons/islands/state/table-key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk/save,/v1/addons/islands/state/table/key-value/bulk,/v1/addons/islands/state/table/key-value/bulk-load,/v1/addons/islands/state/table/load,/v1/addons/islands/state/table/bulk-set",
            "CloudIslands-Core-Addon-State-Bulk-Compatibility" to "table-key-value-and-table/key-value-routes-share-table-prefix-flattened-storage",
            "CloudIslands-Multi-Node-Pool-Support" to "true",
            "CloudIslands-Multi-Node-Identity-Policy" to "unique-node-id-and-unique-pool-velocity-server-name",
            "CloudIslands-Multi-Node-Duplicate-Guard" to "routing-diagnostics-report-duplicate-velocity-server-name-node-count",
            "CloudIslands-Node-State-Policy" to "READY,SOFT_FULL,HARD_FULL,DRAINING,SHUTTING_DOWN,DOWN,WARMING",
            "CloudIslands-Node-Drain-Policy" to "NodeDrainPolicy-drain-blocks-new-activation-route-candidates-active-islands-remain-single-owner-until-save-migrate-sweep",
            "CloudIslands-Node-Routing-Hard-Rules" to "pool-match,ready-or-soft-full,fresh-heartbeat,hard-cap-open,activation-queue-open,object-storage-available,template-supported,min-node-version,not-default-identity",
            "CloudIslands-Node-Routing-Score-Weights" to "players=0.25,activeIslands=0.15,mspt=0.25,activationQueue=0.15,chunkLoad=0.10,memory=0.05,recentFailure=0.05",
            "CloudIslands-Core-Security-Policy" to "mtls-or-api-token-ip-allowlist-admin-permission-audit-rate-limit",
            "CloudIslands-Core-Control-Plane" to "http-api-and-redis-streams-no-critical-plugin-messaging-dependency",
            "CloudIslands-Core-Job-Completion-Contract" to "reject-stale-fencing-token-and-stale-node-completion-before-runtime-state-or-snapshot-write",
            "CloudIslands-Core-Migration-Policy" to "drain-save-snapshot-unload-restore-ticket-based-rejoin",
            "CloudIslands-Core-Active-Full-Policy" to "active-island-stays-single-node-members-use-reserved-slots-visitors-denied-or-queued",
            "CloudIslands-Core-Global-Event-Policy" to "redis-stream-backed-event-log-with-local-paper-event-bridging",
            "CloudIslands-Core-Redis-Key-Contract" to "ci:server,ci:player,ci:island,ci:lock,ci:stream",
            "CloudIslands-Core-Redis-TTL-Contract" to "heartbeat=5s,route-ticket=30s,player=300s,island-summary=60s,permissions=30s,locks=10-60s",
            "CloudIslands-Core-Fencing-Policy" to "redis-lock-fast-path-postgresql-row-lock-final-authority-fencing-token-stale-write-guard",
            "CloudIslands-Core-Schema-Guard-Range" to "V17-placement,V18-route-job-indexes,V19-node-identity,V20-active-state,V21-V24-route-ticket,V38-fencing,V39-V54-value-guards",
            "CloudIslands-Core-Level-Worth-Policy" to "event-delta-plus-periodic-full-scan-reconciliation",
            "CloudIslands-Core-Ranking-Policy" to "dirty-queue-batch-recalculation-redis-ranking-cache",
            "CloudIslands-Core-Block-Value-Policy" to "material-worth-level-limit-values-backed-by-core-api",
            "CloudIslands-Core-Level-Worth-Formula" to "level=floor(total_level_points/1000),worth=SUM_BLOCK_VALUES",
            "CloudIslands-Core-Ranking-Snapshot-Table" to "island_rank_snapshots(island_id,level,worth,member_count,updated_at)",
            "CloudIslands-Core-Upgrade-Policy" to "config-driven-upgrade-rules-with-economy-abstracted-purchase-flow",
            "CloudIslands-Core-Upgrade-Rule-Resource" to "rules/upgrades.yaml",
            "CloudIslands-Core-Upgrade-Economy-Bridge" to "withdraw,deposit,balance-return-completable-future",
            "CloudIslands-Core-Failure-Policy" to "node-down-recovery-required-core-down-degraded-active-play-redis-down-db-source-object-storage-down-local-active-play-retry-save",
            "CloudIslands-Core-Cache-Invalidation-Policy" to "writes-publish-global-events-for-local-cache-and-redis-invalidation",
            "CloudIslands-Core-Snapshot-Retention" to "hourly=24,daily=7,weekly=4,manual=50,compress=true,checksum=SHA-256",
            "CloudIslands-Core-Observability-Metrics" to "nodes,node-players,node-mspt,node-active-islands,activation-queue,activation-seconds,save-seconds,snapshot-seconds,route-tickets,permission-checks,jobs,storage,database,redis",
            "CloudIslands-Core-Admin-Dashboard-Inputs" to "node-load,mspt,active-islands,activation-latency,save-failures,route-failures,redis-latency,db-pool,object-storage-failures",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblockAPI-Compile-Dependency" to "false",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
