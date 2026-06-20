plugins { `java-library` }

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(project(":cloudislands-api"))
    testImplementation(project(":cloudislands-api"))
    testImplementation(project(":cloudislands-testkit"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("projectVersion", project.version)
    inputs.property("paperApiBaseline", libs.versions.minecraft.baseline.get())
    filesMatching("plugin.yml") {
        expand(
            "projectVersion" to project.version,
            "paperApiBaseline" to libs.versions.minecraft.baseline.get()
        )
    }
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Example-Addon" to "cloudislands-example-addon",
            "CloudIslands-Example-Purpose" to "developer-kit-reference-addon",
            "CloudIslands-Example-Certification" to "validated-by-cloudislands-testkit-ApiContractVerifier"
        )
    }
}
