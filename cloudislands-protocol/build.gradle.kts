plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Protocol-Role" to "stable-dto-contract-between-core-velocity-paper-and-addons",
            "CloudIslands-Protocol-DTOs" to "route-tickets,node-heartbeats,island-runtime,bulk-state-requests,bulk-state-results,setup-status,migration-jobs,command-list-pages",
            "CloudIslands-Protocol-Version-Policy" to "explicit-versioned-request-response-records-no-server-local-object-leakage",
            "CloudIslands-Protocol-Routing-Contract" to "logical-island-and-player-targets-hide-physical-node-names",
            "CloudIslands-Protocol-Failure-Contract" to "degraded-retry-fallback-and-admin-visible-error-codes"
        )
    }
}
