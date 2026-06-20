plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Storage-Backends" to "S3,MINIO,LOCAL_FILESYSTEM",
            "CloudIslands-Storage-Backend-Policy" to "StorageBackendPolicy-s3-or-minio-primary-object-storage-with-local-filesystem-fallback",
            "CloudIslands-Storage-Bundle-Policy" to "portable-island-bundle-with-manifest-chunks-entities-block-entities-checksum",
            "CloudIslands-Storage-Bundle-Restore-Contract" to "BundleRestorePolicy-manifest-required-portable-required-SHA-256-zstd-node-agnostic-remap",
            "CloudIslands-Storage-Snapshot-Retention" to "hourly=24,daily=7,weekly=4,manual=50",
            "CloudIslands-Storage-Snapshot-Checksum" to "SHA-256",
            "CloudIslands-Storage-Rollback-Policy" to "lock-restoring-lobby-transfer-pre-restore-snapshot-restore-runtime-reset-reactivate"
        )
    }
}
