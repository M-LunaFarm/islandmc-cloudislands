plugins { `java-library` }

fun embeddedOutput(projectName: String) =
    (project(projectName).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer)
        .named("main").get().output

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-storage"))
    compileOnly("org.postgresql:postgresql:42.7.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    listOf(
        ":cloudislands-api",
        ":cloudislands-storage"
    ).forEach { embeddedProject ->
        from(embeddedOutput(embeddedProject))
    }
    manifest {
        attributes(
            "CloudIslands-Migration-Source" to "SuperiorSkyblock2-read-only-scan",
            "CloudIslands-Migration-Targets" to "island,owner,members,roles,permissions,location,size,homes,warps,bans,level,worth,upgrades,flags,block-values,limits,missions",
            "CloudIslands-Migration-Flow" to "scan,manifest,dry-run,conflicts,approval,db-import,world-extract,bundle,checksum,activate-test,rollback",
            "CloudIslands-Migration-Commands" to "core-admin-api:scan,status,dryrun,dry-run,extract,import,verify,rollback",
            "CloudIslands-Migration-Approval" to "explicit-admin-token-required-before-import",
            "CloudIslands-Migration-Safety-Policy" to "MigrationSafetyPolicy-input-only-read-only-actions-write-actions-approval-required",
            "CloudIslands-Migration-Rollback-Policy" to "rollback-plan-removes-only-cloudislands-imported-state",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false",
            "SuperiorSkyblock2-CompileOnly-Dependency" to "false",
            "SuperiorSkyblock2-Live-Provider-Hooks" to "false"
        )
    }
}
