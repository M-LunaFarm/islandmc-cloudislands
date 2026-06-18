plugins { `java-library` }

fun embeddedOutput(projectName: String) =
    (project(projectName).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer)
        .named("main").get().output

val embeddedProjects = listOf(
    ":cloudislands-api",
    ":cloudislands-protocol",
    ":cloudislands-core-client",
    ":cloudislands-common"
)

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-core-client"))
    implementation(project(":cloudislands-common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("CloudIslands-Velocity")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(embeddedProjects.map { project(it).tasks.named("jar") })
    embeddedProjects.forEach { embeddedProject ->
        from(embeddedOutput(embeddedProject))
    }
    manifest {
        attributes(
            "CloudIslands-Multi-Node-Pool-Support" to "true",
            "CloudIslands-Multi-Node-Identity-Policy" to "velocity-server-names-match-island-pool-prefix-and-paper-node-velocity-server-name",
            "CloudIslands-Multi-Node-Scale-Example" to "island-1,island-2,island-3,island-4,island-5,island-6",
            "CloudIslands-Node-Routing-Policy" to "least-loaded-for-new-or-inactive-current-active-node-for-active-islands",
            "CloudIslands-Node-Full-Policy" to "hard-full-deny-or-queue-soft-full-avoid-new-activations",
            "CloudIslands-Network-Forwarding-Policy" to "velocity-modern-forwarding-required",
            "CloudIslands-Network-Forwarding-Secret-Path" to "security.forwarding-secret",
            "CloudIslands-Backend-Access-Policy" to "BackendAccessPolicy-modern-forwarding-paper-backends-private-proxy-only",
            "CloudIslands-Plugin-Messaging-Policy" to "block-cloudislands-plugin-messages-by-default-do-not-use-for-core-control-plane",
            "CloudIslands-Velocity-Setup-Database-Policy" to "setup.database.type-core-api-only-velocity-never-owns-island-state",
            "CloudIslands-Velocity-Setup-Fallback-Policy" to "core-api-primary-with-config-visible-fallback-order-and-lobby-fallback-on-route-failure",
            "CloudIslands-Velocity-Core-API-Setup-Path" to "setup.core-api",
            "CloudIslands-Route-Ticket-Policy" to "velocity-issues-paper-consumes-ttl-bound-route-tickets",
            "CloudIslands-Route-Preparation-Progress" to "actionbar-and-bossbar-progress-without-physical-node-name-exposure",
            "CloudIslands-Logical-Island-View" to "hide-physical-island-node-names-from-players",
            "CloudIslands-Velocity-Command-Policy" to "global-is-and-island-command-entrypoint-for-all-backend-servers",
            "CloudIslands-Velocity-Command-List-Policy" to "one-line-one-command-page-size-12",
            "CloudIslands-Velocity-Role-Policy" to "global-command-route-ticket-connect-fallback-server-state-entrypoint",
            "CloudIslands-Velocity-No-World-Execution-Policy" to "velocity-never-runs-island-worlds-or-writes-island-bundles",
            "CloudIslands-Velocity-Failure-Recovery" to "pending-route-clear-fallback-server-and-ticket-expiry",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
