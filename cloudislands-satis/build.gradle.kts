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
            "CloudIslands-Core-Depends-On-Addon" to "false",
            "CloudIslands-Addon-Removal-Safe" to "true",
            "CloudIslands-Addon-Data-Retention" to "preserve-addon-state-by-island-uuid",
            "CloudIslands-Addon-State-Authority" to "core-api-table-key-value-or-shared-database",
            "CloudIslands-Addon-Database-Setup-Path" to "setup.database",
            "CloudIslands-Addon-Database-Supported-Backends" to "CORE_API,POSTGRESQL,MYSQL,MARIADB,SQLITE",
            "CloudIslands-Addon-Database-Shared-State-Safe" to "CORE_API,POSTGRESQL,MYSQL,MARIADB",
            "CloudIslands-Addon-Database-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE",
            "CloudIslands-Addon-Database-Split-Brain-Warning" to "keep-shared-backend-before-sqlite-for-multi-island-node-pools",
            "CloudIslands-Satis-Placement-Source-Policy" to "record-core-payload-or-paper-allocator-on-activate-and-migrate",
            "CloudIslands-Addon-Feature-Gates" to "commands,machines,storage,factories,generators,upgrades,missions,menus,gui,lifecycle,resource-nodes,market,contracts,research,maintenance,placeholders,migration,addon-state,route-events",
            "CloudIslands-Addon-Feature-Disable-Policy" to "disabled-features-preserve-data-and-skip-runtime-components",
            "CloudIslands-Addon-Island-Move-Policy" to "island-uuid-stable-remap-active-world-and-cell",
            "CloudIslands-Satis-Legacy-Migration-Source" to "sqlite",
            "CloudIslands-Satis-Legacy-Migration-Approval" to "CONFIRM_IMPORT",
            "CloudIslands-Satis-Legacy-Migration-Read-Only" to "scan,dryrun,verify",
            "CloudIslands-Satis-Legacy-Migration-Import-Policy" to "cross-backend-sqlite-copy-insert-ignore-existing-rows",
            "CloudIslands-Satis-Legacy-Migration-Rollback" to "sqlite-snapshot-restore-or-manual-shared-backend-restore",
            "CloudIslands-Addon-API-Only" to "true",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
