plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    api(project(":cloudislands-protocol"))
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Core-Client-Role" to "shared-core-api-client-for-paper-velocity-and-official-addons",
            "CloudIslands-Core-Client-Access-Policy" to "all-mutating-state-access-goes-through-core-api-no-addon-direct-db-requirement",
            "CloudIslands-Core-Client-Setup-Policy" to "mysql-postgresql-or-core-api-selected-by-config-with-safe-fallback-status",
            "CloudIslands-Core-Client-Bulk-State-Endpoints" to "addon-table-bulk-save,addon-key-value-bulk-save,addon-state-flush-status",
            "CloudIslands-Core-Client-Satis-Contract" to "satis-state-addressed-by-cloudislands-island-uuid-and-remapped-on-node-move",
            "CloudIslands-Core-Client-Security" to "timeouts-auth-token-admin-token-and-no-physical-node-name-exposure"
        )
    }
}
