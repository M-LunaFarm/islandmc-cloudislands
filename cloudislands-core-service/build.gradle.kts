plugins { application }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-storage"))
    implementation(project(":cloudislands-migration"))
    runtimeOnly("org.postgresql:postgresql:42.7.7")
    runtimeOnly("com.mysql:mysql-connector-j:9.1.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.1")
}

application {
    mainClass.set("kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication")
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Core-Setup-Database-Path" to "setup.database",
            "CloudIslands-Core-Setup-Database-Supported-Targets" to "POSTGRESQL,MYSQL,MARIADB,CORE_API",
            "CloudIslands-Core-JDBC-Native-Backend" to "POSTGRESQL,MYSQL,MARIADB",
            "CloudIslands-Core-JDBC-Auto-Schema-Path" to "setup.database.auto-schema",
            "CloudIslands-Core-JDBC-Auto-Schema-Policy" to "explicit-opt-in-mysql-mariadb-bootstrap",
            "CloudIslands-Core-JDBC-Auto-Schema-Resource" to "/db/mysql/V1__cloudislands_mysql_schema.sql",
            "CloudIslands-Core-JDBC-Auto-Schema-History-Table" to "cloudislands_schema_bootstrap",
            "CloudIslands-Core-JDBC-Auto-Schema-Retry-Policy" to "ignore-existing-schema-objects-and-mark-bootstrap-after-complete-apply",
            "CloudIslands-Core-JDBC-Auto-Schema-Guard-Policy" to "generated-columns-enforce-active-unique-guards-for-mysql-mariadb",
            "CloudIslands-Core-JDBC-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC",
            "CloudIslands-Core-Unsupported-JDBC-Policy" to "safe-fallback-with-shared-jdbc-core-api-or-in-memory",
            "CloudIslands-Core-Addon-State-Bulk-Endpoints" to "/v1/addons/state/table/bulk,/v1/addons/state/table-key-value/bulk-save,/v1/addons/state/table/key-value/bulk-save,/v1/addons/state/table/key-value/bulk/save,/v1/addons/state/table/key-value/bulk",
            "CloudIslands-Core-Addon-Island-State-Bulk-Endpoints" to "/v1/addons/islands/state/table/bulk,/v1/addons/islands/state/table-key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk/save,/v1/addons/islands/state/table/key-value/bulk",
            "CloudIslands-Core-Addon-State-Bulk-Compatibility" to "table-key-value-and-table/key-value-routes-share-table-prefix-flattened-storage",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}
