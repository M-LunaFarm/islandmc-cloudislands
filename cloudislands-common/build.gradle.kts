plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Redis-Key-Prefix" to "ci:",
            "CloudIslands-Redis-Key-Families" to "server,player,island,route-ticket,lock,stream",
            "CloudIslands-Redis-Streams" to "ci:stream:jobs,ci:stream:events,ci:stream:audit",
            "CloudIslands-Redis-TTL-Policy" to "heartbeat=5s,route-ticket=30s,player=300s,island-summary=60s,island-runtime=30s,permissions=30s,lock=10-60s",
            "CloudIslands-Fencing-Token-Key" to "ci:island:{islandId}:fencing-token",
            "CloudIslands-Runtime-State-Policy" to "IslandRuntimeStatePolicy-single-active-transition-owner-fencing-token-stale-write-guard",
            "CloudIslands-Node-Drain-Policy" to "NodeDrainPolicy-drain-blocks-new-activation-route-candidates-keeps-active-islands-single-owner",
            "CloudIslands-Storage-Outage-Policy" to "StorageOutagePolicy-active-islands-stay-local-save-failures-queued-for-retry",
            "CloudIslands-Backend-Access-Policy" to "BackendAccessPolicy-velocity-modern-forwarding-proxy-only-paper-backends",
            "CloudIslands-Protection-Decision-Policy" to "ProtectionDecisionPolicy-region-index-local-cache-only-no-sync-io",
            "CloudIslands-Protection-System-Policy" to "world-chunk-region-index-bounding-box-island-id-local-permission-cache",
            "CloudIslands-Permission-System-Policy" to "admin-bypass-owner-explicit-role-trusted-visitor-flags-default-deny",
            "CloudIslands-Permission-Sync-Source-Policy" to "local-region-index-and-permission-cache-only",
            "CloudIslands-Setup-Backend-Fallback-Policy" to "SetupBackendFallbackPolicy-shared-db-or-core-api-before-unsupported-local-fallback",
            "CloudIslands-Cache-Invalidation-Targets" to "player,island-summary,runtime,members,permissions,flags,warps,node-heartbeat",
            "CloudIslands-Cache-Invalidation-Redis-Key-Mapper" to "CacheInvalidationPlan.redisKeysFor-event-to-RedisKeys",
            "CloudIslands-Cache-Three-Level-Policy" to "L1-paper-velocity-local-memory,L2-redis,L3-postgresql",
            "CloudIslands-Cache-Global-Event-Fanout" to "core-api-write-event-to-island-nodes-lobby-and-velocity-route-cache",
            "CloudIslands-Failure-Handling-Policy" to "node-down-core-api-down-redis-down-object-storage-down",
            "CloudIslands-Package-Modules" to "api,common,protocol,core-client,core-service,velocity,paper,satis,storage,migration,testkit,bom",
            "CloudIslands-Package-External-Addon" to "cloudislands-satis",
            "CloudIslands-Package-Primary-Services" to "velocity-router,paper-agent,core-api,storage,migration",
            "CloudIslands-Config-Surface-Policy" to "velocity-paper-core-configs-keep-goal-setup-routing-storage-security-keys",
            "CloudIslands-Satis-Policy-Owner" to "cloudislands-common",
            "CloudIslands-Satis-Recommended-Mode" to "external-addon-built-in-compatible",
            "CloudIslands-Satis-Core-Dependency-Policy" to "core-never-depends-on-cloudislands-satis-jar",
            "CloudIslands-Satis-Feature-Pack-Policy" to "optional-content-layer-feature-gated-by-root-and-child-config",
            "CloudIslands-Satis-State-Policy" to "shared-state-by-island-uuid-survives-node-move-addon-disable-and-addon-reinstall"
        )
    }
}
