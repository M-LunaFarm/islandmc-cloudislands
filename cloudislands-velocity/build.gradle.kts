plugins { `java-library` }

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-core-client"))
    implementation(project(":cloudislands-common"))
}

tasks.jar {
    archiveBaseName.set("CloudIslands-Velocity")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "CloudIslands-Multi-Node-Pool-Support" to "true",
            "CloudIslands-Multi-Node-Identity-Policy" to "velocity-server-names-match-island-pool-prefix-and-paper-node-velocity-server-name",
            "CloudIslands-Multi-Node-Scale-Example" to "island-1,island-2,island-3,island-4,island-5,island-6",
            "CloudIslands-Node-Routing-Policy" to "least-loaded-for-new-or-inactive-current-active-node-for-active-islands",
            "CloudIslands-Node-Full-Policy" to "hard-full-deny-or-queue-soft-full-avoid-new-activations",
            "CloudIslands-Network-Forwarding-Policy" to "velocity-modern-forwarding-required",
            "CloudIslands-Network-Forwarding-Secret-Path" to "security.forwarding-secret",
            "CloudIslands-Plugin-Messaging-Policy" to "block-cloudislands-plugin-messages-by-default-do-not-use-for-core-control-plane",
            "CloudIslands-Velocity-Setup-Database-Policy" to "setup.database.type-core-api-only-velocity-never-owns-island-state",
            "CloudIslands-Velocity-Setup-Fallback-Policy" to "core-api-primary-with-config-visible-fallback-order-and-lobby-fallback-on-route-failure",
            "CloudIslands-Velocity-Core-API-Setup-Path" to "setup.core-api",
            "CloudIslands-Route-Ticket-Policy" to "velocity-issues-paper-consumes-ttl-bound-route-tickets",
            "CloudIslands-Logical-Island-View" to "hide-physical-island-node-names-from-players",
            "CloudIslands-Velocity-Command-Policy" to "global-is-and-island-command-entrypoint-for-all-backend-servers",
            "CloudIslands-Velocity-Command-List-Policy" to "one-line-one-command-page-size-12",
            "CloudIslands-Velocity-Failure-Recovery" to "pending-route-clear-fallback-server-and-ticket-expiry",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
