plugins { `java-library` }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-API-Read-Policy" to "queries-available-from-every-server-through-core-client-or-cache",
            "CloudIslands-API-Write-Policy" to "writes-go-through-core-api-no-paper-event-direct-db-writes",
            "CloudIslands-API-Event-Coverage" to "create,activate,deactivate,visit,member,role,permission,flag,level,worth,warp,biome,upgrade",
            "CloudIslands-API-Addon-State-Bulk" to "table-and-table-key-value-bulk-save-contracts",
            "CloudIslands-API-Satis-Integration" to "external-addon-or-built-in-compatible-cloudislands-state-authority"
        )
    }
}
