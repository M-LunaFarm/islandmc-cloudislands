plugins {
    `java-library`
}

allprojects {
    group = "kr.lunaf.cloudislands"
    version = "0.1.0"
}

subprojects {
    if (name != "cloudislands-bom") {
        apply(plugin = "java-library")

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        }
    }
}
