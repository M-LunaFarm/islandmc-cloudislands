plugins { application }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-storage"))
    runtimeOnly("org.postgresql:postgresql:42.7.7")
}

application {
    mainClass.set("kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication")
}
