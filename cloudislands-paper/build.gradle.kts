plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

val pluginProjectVersion = version.toString()

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.plan.api)
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-core-client"))
    implementation(project(":cloudislands-storage"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testImplementation(libs.plan.api)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("projectVersion", pluginProjectVersion)
    inputs.property("paperApiBaseline", libs.versions.minecraft.baseline.get())
    filesMatching("plugin.yml") {
        expand(
            "projectVersion" to pluginProjectVersion,
            "paperApiBaseline" to libs.versions.minecraft.baseline.get()
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("CloudIslands-Paper")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    manifest {
        attributes(
            "CloudIslands-Multi-Node-Pool-Support" to "true",
            "CloudIslands-Multi-Node-Identity-Policy" to "unique-node-id-and-unique-velocity-server-name-per-island-node",
            "CloudIslands-Multi-Node-Shared-State-Policy" to "shared-core-database-and-shared-object-storage-required",
            "CloudIslands-Node-Heartbeat-Cap-Keys" to "node.soft-player-cap,node.hard-player-cap,node.reserved-slots,node.max-active-islands,node.max-activation-queue",
            "CloudIslands-Node-Routing-Hard-Rules" to "ready-or-soft-full,fresh-heartbeat,hard-cap-open,activation-queue-open,object-storage-available,template-supported",
            "CloudIslands-Network-Forwarding-Policy" to "velocity-modern-forwarding-required",
            "CloudIslands-Network-Forwarding-Secret-Path" to "security.forwarding-secret",
            "CloudIslands-Backend-Access-Policy" to "BackendAccessPolicy-proxy-only-paper-backends-route-session-required",
            "CloudIslands-Plugin-Messaging-Policy" to "no-core-control-plane-over-plugin-messages-bungeecord-connect-only-for-proxy-transfer-fallback",
            "CloudIslands-Route-Session-Security" to "paper-join-requires-core-published-route-session-and-forwarding-secret-before-island-teleport",
            "CloudIslands-Route-Ticket-Policy" to "velocity-issues-paper-consumes-ttl-bound-route-tickets",
            "CloudIslands-Route-Preparation-Progress" to "command-route-and-ticket-consume-use-shared-actionbar-bossbar-progress-policy",
            "CloudIslands-Logical-Island-View" to "hide-physical-island-node-names-from-players",
            "CloudIslands-Paper-Event-Bridge" to "global-events-to-bukkit-events-and-cache-invalidation",
            "CloudIslands-Paper-Direct-Write-Policy" to "no-direct-core-db-writes-use-core-api-client",
            "CloudIslands-Paper-Migration-Join-Policy" to "ticket-validated-teleport-after-restore-or-migrate",
            "CloudIslands-Paper-Agent-Roles" to "LOBBY,ISLAND_NODE",
            "CloudIslands-Paper-Lobby-Role" to "gui-ranking-invites-settings-visit-admin-no-island-world-execution",
            "CloudIslands-Paper-Island-Node-Role" to "activation-save-snapshot-shard-cell-protection-teleport-heartbeat",
            "CloudIslands-Paper-Job-Completion-Payload" to "activation-save-deactivation-delete-completions-carry-job-context-and-fencing-token",
            "CloudIslands-Paper-Command-List-Policy" to "one-line-one-command-page-size-12",
            "CloudIslands-Paper-Protection-Decision-Policy" to "ProtectionDecisionPolicy-region-index-local-cache-only-no-sync-core-api-http-db-redis-on-hot-path",
            "CloudIslands-Paper-Protection-Event-Coverage" to "block,dependent-block-break,block-growth,crops-growth,tree-growth,egg-lay,player-time,player-weather,natural-spread,spawner-break,stacked-spawner-spawn,painting,item-frame,leash-knot,turtle-egg-trample,wind-charge,soft-explosion,ghast-fireball,interact,bucket,bucket-dispense,entity-bucket,lectern-book,special-teleport,portal-player,portal-entity,projectile-launch,projectile-pickup,frost-walker,creeper-ignite,entity-name,sculk,raid-trigger,mob-target,brush,dye,saddle,inventory,entity-damage,explosion,hanging,item,armorstand,shear,breed,fish,ride,villager-trade,leash,vehicle,fire,spread,decay,fluid,fertilize,structure-growth",
            "CloudIslands-Paper-Level-Worth-Policy" to "serialized-block-deltas-plus-tick-budgeted-deduplicated-periodic-island-level-scan-with-concurrent-mutation-rejection",
            "CloudIslands-Paper-Generator-Policy" to "config-driven-generator-rules-blockform-and-fluid-collision-replacement",
            "CloudIslands-Paper-Generator-Rule-Resource" to "rules/generators.yaml",
            "CloudIslands-Paper-Generator-Event-Policy" to "BlockFormEvent,BlockFromToEvent,fluid-collision-detection",
            "CloudIslands-Paper-Generator-Level-Cache" to "island-upgrade-generator-level-cache-ttl-30s",
            "CloudIslands-Paper-GUI-Coverage" to "main,member,permission,flag,ranking,node-admin,bank,warp,invite,ban,log,role,danger,snapshot,upgrade,biome,limit",
            "CloudIslands-Paper-GUI-Main-Menu" to "home,create,visit,members,permissions,upgrades,warps,ranking,missions,admin",
            "CloudIslands-Paper-GUI-Node-Admin" to "node-load-drain-undrain-view-islands-move-load-shutdown-safe",
            "CloudIslands-Paper-Config-Surface" to "node,core-api,redis,storage,island-node,protection,heartbeat,routing",
            "CloudIslands-Paper-Degraded-Mode-Policy" to "core-down-active-island-local-protection-and-limited-teleport-object-storage-down-active-local-play",
            "CloudIslands-Paper-Safe-Teleport-Policy" to "async-chunk-load-main-thread-block-scan-centered-destination-boundary-and-final-revalidation",
            "CloudIslands-Paper-Entity-Removal-Accounting" to "despawn-enter-block-out-of-world-transformation-counted;death-break-destroy-deduplicated;chunk-unload-ignored",
            "CloudIslands-Paper-Async-Player-Access" to "core-completions-return-to-main-thread-before-permission-session-inventory-or-economy-preflight-access",
            "CloudIslands-Paper-Warehouse-Item-Fidelity" to "material-amount-schema-accepts-only-metadata-free-items;custom-named-enchanted-damaged-container-items-rejected-before-removal",
            "CloudIslands-Paper-Warehouse-Settlement-Recovery" to "player-pdc-marker-fixed-idempotency-key-online-resolution-reconnect-replay-no-offline-inventory-access",
            "CloudIslands-Paper-Storage-Outage-Policy" to "StorageOutagePolicy-active-islands-stay-local-periodic-and-empty-save-failures-queued-for-retry",
            "CloudIslands-Paper-Storage-Backend-Policy" to "S3-or-MINIO-shared-object-storage-LOCAL_FILESYSTEM-fallback",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}

tasks.jar {
    enabled = false
}
