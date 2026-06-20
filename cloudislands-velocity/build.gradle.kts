plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

val generatedBuildInfoDir = layout.buildDirectory.dir("generated/sources/build-info/java")
val generateBuildInfo by tasks.registering {
    inputs.property("projectVersion", project.version.toString())
    outputs.dir(generatedBuildInfoDir)
    doLast {
        val packageDir = generatedBuildInfoDir.get().asFile.resolve("kr/lunaf/cloudislands/velocity")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.java").writeText(
            """
            package kr.lunaf.cloudislands.velocity;

            public final class BuildInfo {
                public static final String VERSION = "${project.version}";

                private BuildInfo() {
                }
            }
            """.trimIndent() + System.lineSeparator()
        )
    }
}

sourceSets {
    named("main") {
        java.srcDir(generatedBuildInfoDir)
    }
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-core-client"))
    implementation(project(":cloudislands-common"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.velocity.api)
}

tasks.compileJava {
    dependsOn(generateBuildInfo)
}

tasks.named("sourcesJar") {
    dependsOn(generateBuildInfo)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("CloudIslands-Velocity")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
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
            "CloudIslands-Velocity-Persistent-State-Policy" to "core-api-only-velocity-never-owns-island-state",
            "CloudIslands-Velocity-Setup-Fallback-Policy" to "core-api-primary-with-config-visible-fallback-order-and-lobby-fallback-on-route-failure",
            "CloudIslands-Velocity-Core-API-Setup-Path" to "setup.core-api",
            "CloudIslands-Velocity-Config-Surface" to "plugin,core-api,routing,commands,messages,security,health",
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
}

tasks.jar {
    enabled = false
}
