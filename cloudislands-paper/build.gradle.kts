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
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
