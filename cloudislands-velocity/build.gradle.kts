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
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
