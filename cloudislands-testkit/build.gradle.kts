plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    api(project(":cloudislands-common"))
    api(project(":cloudislands-core-client"))
    api(project(":cloudislands-protocol"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Testkit-Role" to "shared-fixtures-for-core-velocity-paper-addon-and-routing-contract-tests",
            "CloudIslands-Testkit-Fixtures" to "islands,players,route-tickets,node-heartbeats,addon-state-bulk-save,satis-node-move",
            "CloudIslands-Testkit-Scenario-Coverage" to "multi-node-routing,soft-full-fallback,route-ticket-consume,addon-removal-safe,satis-a-b-node-move,superiorskyblock2-migration-dry-run",
            "CloudIslands-Testkit-Api-Contract" to "addon-runtime-api-version-contract-metadata-threading-event-delivery-timeout-retry-core-failure-policies",
            "CloudIslands-Testkit-Certification" to "addons-must-pass-ApiContractVerifier-before-certification",
            "CloudIslands-Testkit-Certification-Matrix" to "packaging,metadata,runtime-api,removal-safety,event-delivery,threading,core-failure,timeout-retry,testkit-policy",
            "CloudIslands-Testkit-Cluster-Smoke" to "two-core,velocity,lobby-paper,two-island-paper,postgres,redis,object-storage,production-ga-drill-evidence,failure-injection",
            "CloudIslands-Testkit-Node-Pool-Scale" to "supports-2-to-6-island-node-fixture-sets-with-unique-node-ids-and-server-names",
            "CloudIslands-Testkit-Storage-Backends" to "postgresql,mysql,mariadb,core-api,in-memory-fallback"
        )
    }
}
