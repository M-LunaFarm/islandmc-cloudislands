plugins { `java-library` }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-core-client"))
    implementation(project(":cloudislands-storage"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("CloudIslands-Paper")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "CloudIslands-Multi-Node-Pool-Support" to "true",
            "CloudIslands-Multi-Node-Identity-Policy" to "unique-node-id-and-unique-velocity-server-name-per-island-node",
            "CloudIslands-Multi-Node-Shared-State-Policy" to "shared-core-database-and-shared-object-storage-required",
            "CloudIslands-Node-Heartbeat-Cap-Keys" to "node.soft-player-cap,node.hard-player-cap,node.reserved-slots,node.max-active-islands,node.max-activation-queue",
            "CloudIslands-Node-Routing-Hard-Rules" to "ready-or-soft-full,fresh-heartbeat,hard-cap-open,activation-queue-open,object-storage-available,template-supported",
            "CloudIslands-Network-Forwarding-Policy" to "velocity-modern-forwarding-required",
            "CloudIslands-Network-Forwarding-Secret-Path" to "security.forwarding-secret",
            "CloudIslands-Route-Ticket-Policy" to "velocity-issues-paper-consumes-ttl-bound-route-tickets",
            "CloudIslands-Logical-Island-View" to "hide-physical-island-node-names-from-players",
            "CloudIslands-Paper-Event-Bridge" to "global-events-to-bukkit-events-and-cache-invalidation",
            "CloudIslands-Paper-Direct-Write-Policy" to "no-direct-core-db-writes-use-core-api-client",
            "CloudIslands-Paper-Migration-Join-Policy" to "ticket-validated-teleport-after-restore-or-migrate",
            "CloudIslands-Paper-Agent-Roles" to "LOBBY,ISLAND_NODE",
            "CloudIslands-Paper-Lobby-Role" to "gui-ranking-invites-settings-visit-admin-no-island-world-execution",
            "CloudIslands-Paper-Island-Node-Role" to "activation-save-snapshot-shard-cell-protection-teleport-heartbeat",
            "CloudIslands-Paper-Command-List-Policy" to "one-line-one-command-page-size-12",
            "CloudIslands-Paper-Protection-Decision-Policy" to "memory-cache-only-in-sync-events-no-http-or-db-on-hot-path",
            "CloudIslands-Paper-Protection-Event-Coverage" to "block,interact,bucket,inventory,entity-damage,explosion,hanging,item,armorstand,shear,leash,vehicle,fire,spread,decay,fluid",
            "CloudIslands-Paper-Level-Worth-Policy" to "block-delta-reporter-plus-periodic-island-level-scan",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
