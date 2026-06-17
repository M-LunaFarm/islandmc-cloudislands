plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
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
            "CloudIslands-Cache-Invalidation-Targets" to "player,island-summary,runtime,members,permissions,flags,warps,node-heartbeat",
            "CloudIslands-Cache-Invalidation-Redis-Key-Mapper" to "CacheInvalidationPlan.redisKeysFor-event-to-RedisKeys",
            "CloudIslands-Package-Modules" to "api,common,protocol,core-client,core-service,velocity,paper,satis,storage,migration,testkit,bom",
            "CloudIslands-Package-External-Addon" to "cloudislands-satis",
            "CloudIslands-Package-Primary-Services" to "velocity-router,paper-agent,core-api,storage,migration"
        )
    }
}
