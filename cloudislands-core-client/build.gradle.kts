plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    api(project(":cloudislands-protocol"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Core-Client-Role" to "shared-core-api-client-for-paper-velocity-and-official-addons",
            "CloudIslands-Core-Client-Access-Policy" to "all-mutating-state-access-goes-through-core-api-no-addon-direct-db-requirement",
            "CloudIslands-Core-Client-Setup-Policy" to "mysql-postgresql-or-core-api-selected-by-config-with-safe-fallback-status",
            "CloudIslands-Core-Client-Bulk-State-Endpoints" to "/v1/addons/state/table-key-value/bulk-save,/v1/addons/state/table/key-value/bulk-save,/v1/addons/state/table/key-value/bulk/save,/v1/addons/state/table/key-value/bulk,/v1/addons/state/table/bulk,/v1/addons/state/table/bulk-set,/v1/addons/state/table/key-value/bulk-load,/v1/addons/state/table/load,/v1/addons/islands/state/table-key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk/save,/v1/addons/islands/state/table/key-value/bulk,/v1/addons/islands/state/table/bulk,/v1/addons/islands/state/table/bulk-set,/v1/addons/islands/state/table/key-value/bulk-load,/v1/addons/islands/state/table/load",
            "CloudIslands-Core-Client-Satis-Contract" to "satis-state-addressed-by-cloudislands-island-uuid-and-remapped-on-node-move",
            "CloudIslands-Core-Client-Security" to "timeouts-auth-token-admin-token-and-no-physical-node-name-exposure"
        )
    }
}
