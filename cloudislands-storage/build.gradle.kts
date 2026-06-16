plugins { `java-library` }

dependencies {
    api(project(":cloudislands-api"))
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Storage-Backends" to "S3,MINIO,LOCAL_FILESYSTEM",
            "CloudIslands-Storage-Bundle-Policy" to "portable-island-bundle-with-manifest-chunks-entities-block-entities-checksum",
            "CloudIslands-Storage-Snapshot-Retention" to "hourly=24,daily=7,weekly=4,manual=50",
            "CloudIslands-Storage-Snapshot-Checksum" to "SHA-256",
            "CloudIslands-Storage-Rollback-Policy" to "lock-restoring-lobby-transfer-pre-restore-snapshot-restore-runtime-reset-reactivate"
        )
    }
}
