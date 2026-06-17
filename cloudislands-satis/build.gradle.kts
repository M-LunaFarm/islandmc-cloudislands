plugins { `java-library` }

fun embeddedOutput(projectName: String) =
    (project(projectName).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer)
        .named("main").get().output

val embeddedProjects = listOf(":cloudislands-protocol")
val jarDependencyProjects = embeddedProjects + listOf(":cloudislands-api")

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation(project(":cloudislands-protocol"))
    compileOnly(project(":cloudislands-api"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks.test {
    useJUnitPlatform()
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
    dependsOn(jarDependencyProjects.map { project(it).tasks.named("jar") })
    embeddedProjects.forEach { embeddedProject ->
        from(embeddedOutput(embeddedProject))
    }
    manifest {
        attributes(
            "CloudIslands-Addon" to "cloudislands-satis",
            "CloudIslands-Addon-Product-Role" to "official-feature-pack",
            "CloudIslands-Satis-Origin" to "M-LunaFarm/satismc",
            "CloudIslands-Satis-Origin-Dependency-Policy" to "replace-superiorskyblock2-depend-and-api-with-cloudislands-depend-and-api",
            "CloudIslands-Satis-Origin-API-Replacement" to "SuperiorSkyblockAPI-compileOnly-removed-cloudislands-api-used-instead",
            "CloudIslands-Satis-Legacy-Command-Roots" to "factory,sfactory",
            "CloudIslands-Addon-Packaging" to "external-plugin-or-built-in-compatible",
            "CloudIslands-Addon-Integration-Modes" to "EXTERNAL_ADDON,BUILT_IN_COMPATIBLE,DISABLED",
            "CloudIslands-Core-Depends-On-Addon" to "false",
            "CloudIslands-Core-Isolation-Policy" to "core-lifecycle-never-depends-on-satis-jar-or-state",
            "CloudIslands-Addon-Removal-Safe" to "true",
            "CloudIslands-Addon-Data-Retention" to "preserve-addon-state-by-island-uuid",
            "CloudIslands-Addon-State-Authority" to "core-api-table-key-value-or-shared-database",
            "CloudIslands-Addon-Database-Setup-Path" to "setup.database",
            "CloudIslands-Addon-Database-Supported-Backends" to "CORE_API,POSTGRESQL,MYSQL,MARIADB,SQLITE",
            "CloudIslands-Addon-Database-Shared-State-Safe" to "CORE_API,POSTGRESQL,MYSQL,MARIADB",
            "CloudIslands-Addon-Database-Core-API-Setup-Path" to "setup.database.core-api.enabled",
            "CloudIslands-Addon-Database-Core-API-Flattened-Fallback-Path" to "setup.database.core-api.flattened-fallback.enabled",
            "CloudIslands-Addon-Database-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE",
            "CloudIslands-Addon-Database-Split-Brain-Warning" to "keep-shared-backend-before-sqlite-for-multi-island-node-pools",
            "CloudIslands-Addon-Database-Fallback-Safety-Keys" to "database-fallback-risk,database-fallback-production-safe,database-fallback-first-shared-backend,database-fallback-local-position",
            "CloudIslands-Addon-Database-Fallback-Safety-Policy" to "production-safe-when-shared-backend-precedes-local-sqlite",
            "CloudIslands-Addon-Database-Env-Keys" to "CLOUDISLANDS_SATIS_DATABASE_TYPE,CLOUDISLANDS_SATIS_JDBC_URL,CLOUDISLANDS_SATIS_DB_USERNAME,CLOUDISLANDS_SATIS_DB_PASSWORD,CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED,CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER",
            "CloudIslands-Satis-Placement-Source-Policy" to "record-core-payload-or-paper-allocator-on-activate-and-migrate",
            "CloudIslands-Satis-AB-Node-Scenario" to "save-on-source-node-remap-active-world-and-cell-on-target-node-restore-state-by-island-uuid",
            "CloudIslands-Satis-Recovery-Scenario" to "heartbeat-expiry-fencing-token-guards-last-confirmed-state-no-duplicate-tick-or-write",
            "CloudIslands-Satis-Core-API-Bulk-Endpoints" to "global:table/bulk,table-key-value/bulk-save,table/key-value/bulk-save,table/key-value/bulk/save,table/key-value/bulk;island:table/bulk,table-key-value/bulk-save,table/key-value/bulk-save,table/key-value/bulk/save,table/key-value/bulk",
            "CloudIslands-Satis-Core-API-Bulk-Fallback" to "flattened-addon-state-when-enabled",
            "CloudIslands-Addon-Feature-Gates" to "commands,machines,storage,factories,generators,upgrades,missions,menus,gui,lifecycle,resource-nodes,market,contracts,research,maintenance,placeholders,migration,addon-state,route-events",
            "CloudIslands-Addon-Feature-Disable-Policy" to "disabled-features-preserve-data-and-skip-runtime-components",
            "CloudIslands-Addon-Component-Audit-Keys" to "runtime-active-components,runtime-skipped-components,runtime-blocked-components,runtime-component-audit",
            "CloudIslands-Satis-Dirty-Save-State-Keys" to "runtime-dirty-save-running,runtime-dirty-save-pending-writes,runtime-dirty-save-pending-machines,runtime-dirty-save-pending-inventories,runtime-dirty-save-pending-nodes,runtime-dirty-save-pending-islands,runtime-dirty-save-stop-policy",
            "CloudIslands-Satis-Dirty-Save-Stop-Policy" to "runtime-stop-discards-queued-dirty-state-after-explicit-preflush-paths",
            "CloudIslands-Satis-Bulk-Last-Publish-State-Keys" to "last-core-bulk-publish-status,last-core-bulk-publish-mode,last-core-bulk-publish-write-path,last-core-bulk-publish-primary-endpoint,last-core-bulk-publish-fallback-endpoint,last-core-bulk-publish-error,last-core-bulk-publish-pending-retries,last-core-global-bulk-publish-status,last-core-global-bulk-publish-mode,last-core-global-bulk-publish-write-path,last-core-global-bulk-publish-primary-endpoint,last-core-global-bulk-publish-fallback-endpoint,last-core-global-bulk-publish-error,last-core-global-bulk-publish-pending-retries",
            "CloudIslands-Satis-Bulk-Retry-State-Keys" to "addon-state-sync-bulk-max-pending-retries,addon-state-sync-island-bulk-pending-retries,addon-state-sync-island-bulk-retries-queued,addon-state-sync-island-bulk-retries-drained,addon-state-sync-island-bulk-retries-dropped,addon-state-sync-global-bulk-pending-retries,addon-state-sync-global-bulk-retries-queued,addon-state-sync-global-bulk-retries-drained,addon-state-sync-global-bulk-retries-dropped",
            "CloudIslands-Addon-Island-Move-Policy" to "island-uuid-stable-remap-active-world-and-cell",
            "CloudIslands-Satis-Legacy-Migration-Source" to "sqlite",
            "CloudIslands-Satis-Legacy-Migration-Approval" to "CONFIRM_IMPORT or CONFIRM_IMPORT:<dryrun-sha256>",
            "CloudIslands-Satis-Legacy-Migration-Read-Only" to "scan,dryrun,verify",
            "CloudIslands-Satis-Legacy-Migration-Import-Policy" to "cross-backend-sqlite-copy-insert-ignore-existing-rows",
            "CloudIslands-Satis-Legacy-Migration-Rollback" to "sqlite-snapshot-restore-or-shared-backend-table-restore",
            "CloudIslands-Addon-API-Only" to "true",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false",
            "CloudIslands-Satis-Forbidden-Skyblock-Runtime-Providers" to "SuperiorSkyblock2,BentoBox,ASkyBlock",
            "CloudIslands-Satis-Forbidden-Skyblock-Runtime-Action" to "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes"
        )
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
