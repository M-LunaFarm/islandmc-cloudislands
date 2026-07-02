tasks.register("verifyMigrationFixtures") {
    group = "verification"
    description = "Verifies SuperiorSkyblock2 migration fixtures cover the edit.md YAML, JSON, legacy, Korean, broken owner, missing world, and 1000-island samples."
    dependsOn(project(":cloudislands-migration").tasks.named("test"))
    val fixtureRoot = layout.projectDirectory.dir("cloudislands-migration/src/test/resources/fixtures/ss2")
    val reportSource = layout.projectDirectory.file("cloudislands-migration/src/main/java/kr/lunaf/cloudislands/migration/MigrationReport.java")
    val safetySource = layout.projectDirectory.file("cloudislands-migration/src/main/java/kr/lunaf/cloudislands/migration/superior/MigrationSafetyPolicy.java")
    inputs.dir(fixtureRoot)
    inputs.files(reportSource, safetySource)
    doLast {
        val requiredFixtures = listOf("basic-yaml", "basic-json", "legacy-yaml", "korean-names", "broken-owner", "missing-world", "large-1000-islands")
        val missing = requiredFixtures.filterNot { fixtureRoot.dir(it).asFile.isDirectory }
        if (missing.isNotEmpty()) {
            throw GradleException("SS2 migration fixtures missing: ${missing.joinToString(", ")}")
        }
        val largeFixtureCount = fixtureRoot.dir("large-1000-islands").dir("islands").asFile
            .walkTopDown()
            .count { it.isFile && (it.extension == "yml" || it.extension == "yaml" || it.extension == "json") }
        if (largeFixtureCount != 1000) {
            throw GradleException("SS2 large-1000-islands fixture must contain 1000 island files, found $largeFixtureCount")
        }
        val reportText = reportSource.asFile.readText()
        val safetyText = safetySource.asFile.readText()
        if (!reportText.contains("unsupportedFieldCount")) {
            throw GradleException("SS2 dry-run report must expose unsupported field count")
        }
        if (!safetyText.contains("rollbackTargetVerified")) {
            throw GradleException("SS2 migration safety policy must verify rollback targets")
        }
    }
}
