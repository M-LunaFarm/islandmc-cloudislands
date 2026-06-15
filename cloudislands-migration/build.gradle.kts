plugins { `java-library` }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-storage"))
    compileOnly("org.postgresql:postgresql:42.7.7")
}

tasks.jar {
    manifest {
        attributes(
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}
