plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Redis-Key-Prefix" to "ci:",
            "CloudIslands-Redis-Key-Families" to "server,player,island,route-ticket,lock,stream",
            "CloudIslands-Redis-Streams" to "ci:stream:jobs,ci:stream:events,ci:stream:audit",
            "CloudIslands-Redis-TTL-Policy" to "heartbeat=5s,route-ticket=30s,player=300s,island-summary=60s,island-runtime=30s,permissions=30s,lock=10-60s",
            "CloudIslands-Fencing-Token-Key" to "ci:island:{islandId}:fencing-token",
            "CloudIslands-Cache-Invalidation-Targets" to "player,island-summary,runtime,members,permissions,flags,warps,node-heartbeat",
            "CloudIslands-Package-Modules" to "api,common,protocol,core-client,core-service,velocity,paper,satis,storage,migration,testkit,bom",
            "CloudIslands-Package-External-Addon" to "cloudislands-satis",
            "CloudIslands-Package-Primary-Services" to "velocity-router,paper-agent,core-api,storage,migration"
        )
    }
}
