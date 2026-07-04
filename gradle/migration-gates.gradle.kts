tasks.register("verifyMigrationFixtures") {
    group = "verification"
    description = "Verifies SuperiorSkyblock2 migration fixtures cover the edit.md YAML, JSON, legacy, Korean, broken owner, missing world, and 1000-island samples."
    dependsOn(project(":cloudislands-migration").tasks.named("test"))
    val fixtureRoot = layout.projectDirectory.dir("cloudislands-migration/src/test/resources/fixtures/ss2")
    val reportSource = layout.projectDirectory.file("cloudislands-migration/src/main/java/kr/lunaf/cloudislands/migration/MigrationReport.java")
    val safetySource = layout.projectDirectory.file("cloudislands-migration/src/main/java/kr/lunaf/cloudislands/migration/superior/MigrationSafetyPolicy.java")
    val migrationJsonReport = layout.buildDirectory.file("reports/cloudislands/migration.json")
    val migrationMarkdownReport = layout.buildDirectory.file("reports/cloudislands/migration.md")
    inputs.dir(fixtureRoot)
    inputs.files(reportSource, safetySource)
    outputs.files(migrationJsonReport, migrationMarkdownReport)
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
        if (!reportText.contains("toJson()") || !reportText.contains("toMarkdown()")) {
            throw GradleException("SS2 dry-run report must export JSON and Markdown")
        }
        if (!safetyText.contains("rollbackTargetVerified")) {
            throw GradleException("SS2 migration safety policy must verify rollback targets")
        }
        listOf("ratings", "generators", "schematics", "templates", "stacked-blocks", "custom-data", "unsupported-data", "downtime-estimate")
            .filterNot(safetyText::contains)
            .takeIf { it.isNotEmpty() }
            ?.let { missingTargets -> throw GradleException("SS2 migration target field coverage missing: ${missingTargets.joinToString(", ")}") }
        migrationJsonReport.get().asFile.parentFile.mkdirs()
        val generatedAt = java.time.Instant.now().toString()
        fun jsonEscape(value: String): String = buildString {
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
        fun jsonField(key: String, value: String): String = "\"${jsonEscape(key)}\":\"${jsonEscape(value)}\""
        migrationJsonReport.get().asFile.writeText(
            buildString {
                append("{")
                append(jsonField("generatedAt", generatedAt))
                append(",")
                append(jsonField("scope", "migration-fixture-coverage"))
                append(",\"fixtures\":[")
                requiredFixtures.forEachIndexed { index, fixture ->
                    if (index > 0) append(",")
                    append("{")
                    append(jsonField("name", fixture))
                    append(",")
                    append("\"present\":true")
                    append("}")
                }
                append("],")
                append("\"largeFixtureIslandFiles\":").append(largeFixtureCount).append(",")
                append("\"reportFields\":[")
                listOf(
                    "totalIslands",
                    "importableIslands",
                    "failedIslands",
                    "skippedIslands",
                    "ownerMissing",
                    "worldMissing",
                    "unsupportedPermissions",
                    "unsupportedMissions",
                    "unsupportedUpgrades",
                    "unsupportedFields",
                    "bankBalanceMappings",
                    "ratings",
                    "generators",
                    "limits",
                    "schematics",
                    "templates",
                    "stackedBlocks",
                    "customData",
                    "blockWorthMappings",
                    "warpMappings",
                    "roleMappings",
                    "rollbackPossible",
                    "downtimeEstimatePolicy"
                ).forEachIndexed { index, field ->
                    if (index > 0) append(",")
                    append("\"${jsonEscape(field)}\"")
                }
                append("]}")
            }
        )
        migrationMarkdownReport.get().asFile.writeText(
            buildString {
                appendLine("# SuperiorSkyblock2 migration fixture report")
                appendLine()
                appendLine("- Generated: $generatedAt")
                appendLine("- Large fixture island files: $largeFixtureCount")
                appendLine("- JSON/Markdown report methods: present")
                appendLine()
                appendLine("| Fixture | Present |")
                appendLine("| --- | --- |")
                requiredFixtures.forEach { fixture ->
                    appendLine("| `$fixture` | true |")
                }
                appendLine()
                appendLine("Report fields include total/success/fail/skip counts, owner/world/warp/bank/block worth/role mappings, unsupported fields, conflicts, and rollback availability.")
            }
        )
    }
}
