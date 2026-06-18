plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":cloudislands-api"))
        api(project(":cloudislands-common"))
        api(project(":cloudislands-protocol"))
        api(project(":cloudislands-core-client"))
        api(project(":cloudislands-core-service"))
        api(project(":cloudislands-velocity"))
        api(project(":cloudislands-paper"))
        api(project(":cloudislands-satis"))
        api(project(":cloudislands-storage"))
        api(project(":cloudislands-migration"))
        api(project(":cloudislands-testkit"))
    }
}

publishing {
    publications {
        create<MavenPublication>("cloudIslandsBom") {
            from(components["javaPlatform"])
            artifactId = "cloudislands-bom"
        }
    }
}
