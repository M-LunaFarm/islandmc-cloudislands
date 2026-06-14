plugins { `java-library` }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    compileOnly(project(":cloudislands-api"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("CloudIslands-Satis")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "CloudIslands-Addon" to "cloudislands-satis",
            "CloudIslands-Addon-Packaging" to "external-plugin",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
