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
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
