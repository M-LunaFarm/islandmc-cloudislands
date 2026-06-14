plugins { application }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-storage"))
    implementation(project(":cloudislands-migration"))
    runtimeOnly("org.postgresql:postgresql:42.7.7")
}

application {
    mainClass.set("kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication")
}

tasks.jar {
    manifest {
        attributes(
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}
