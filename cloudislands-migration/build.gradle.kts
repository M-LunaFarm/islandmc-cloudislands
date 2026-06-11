plugins { `java-library` }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-storage"))
    compileOnly("org.postgresql:postgresql:42.7.7")
}
