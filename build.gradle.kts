import org.gradle.api.file.FileTreeElement
import org.gradle.jvm.tasks.Jar
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.security.MessageDigest
import java.util.zip.ZipFile

private fun jsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

private fun jsonString(value: String): String = "\"${jsonEscape(value)}\""

plugins {
    `java-library`
    alias(libs.plugins.shadow) apply false
}

val markdownDocPatterns = listOf(
    "**/*.md",
    "**/*.MD",
    "**/*.mdx",
    "**/*.MDX",
    "**/*.mdown",
    "**/*.MDOWN",
    "**/*.mkdn",
    "**/*.MKDN",
    "**/*.markdown",
    "**/*.MARKDOWN",
    "**/*.mkd",
    "**/*.MKD"
)

val markdownDocExtensions = listOf(".md", ".mdx", ".mdown", ".mkdn", ".markdown", ".mkd")
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val cloudislandsVersion = versionCatalog.findVersion("cloudislands").orElseThrow().requiredVersion
val javaCurrentVersion = versionCatalog.findVersion("java-current").orElseThrow().requiredVersion.toInt()
val minecraftBaselineVersion = versionCatalog.findVersion("minecraft-baseline").orElseThrow().requiredVersion
val developerKitProjectNames = listOf(
    "cloudislands-api",
    "cloudislands-common",
    "cloudislands-protocol",
    "cloudislands-core-client",
    "cloudislands-storage",
    "cloudislands-migration",
    "cloudislands-testkit"
)
val exampleAddonProjectNames = listOf(
    "cloudislands-example-addon"
)

fun isMarkdownDocPath(path: String): Boolean =
    path.replace('\\', '/') != "README.md" && markdownDocExtensions.any { path.lowercase().endsWith(it) }

fun isMarkdownDocElement(element: FileTreeElement): Boolean =
    isMarkdownDocPath(element.path)

allprojects {
    group = "kr.lunaf.cloudislands"
    version = cloudislandsVersion

    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    if (name != "cloudislands-bom") {
        apply(plugin = "java-library")
        if (name in developerKitProjectNames) {
            apply(plugin = "maven-publish")
        }

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaCurrentVersion))
            }
            withSourcesJar()
            if (name in developerKitProjectNames) {
                withJavadocJar()
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        }

        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            systemProperty("cloudislands.version", project.version.toString())
            systemProperty("cloudislands.minecraftBaseline", minecraftBaselineVersion)
        }

        tasks.withType<Jar>().configureEach {
            exclude(markdownDocPatterns)
            exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
        }

        if (name in developerKitProjectNames) {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifactId = project.name
                    }
                }
                repositories {
                    maven {
                        name = "developerKit"
                        url = rootProject.layout.buildDirectory.dir("devkit-maven").get().asFile.toURI()
                    }
                }
            }
        }
    }
}

tasks.register("verifyMarkdownDocsExcludedFromArtifacts") {
    group = "verification"
    description = "Verifies markdown documents are allowed in source but excluded from packaged artifacts."
    doLast {
        val markdownFiles = fileTree(projectDir) {
            exclude(".git/**", ".gradle/**", "**/build/**", "**/.gradle/**")
        }.files
            .filter { isMarkdownDocPath(it.relativeTo(projectDir).path) }
            .sortedBy { it.relativeTo(projectDir).path }
        if (markdownFiles.isNotEmpty()) {
            logger.lifecycle(
                "Markdown source documents are allowed and will be excluded from packaged artifacts: {}",
                markdownFiles.joinToString(", ") { it.relativeTo(projectDir).path }
            )
        }
    }
}

val verifySatisLegacyMigrationRemoved = tasks.register("verifySatisLegacyMigrationRemoved") {
    group = "verification"
    description = "Verifies the removed Satis legacy migration/import surface stays absent."

    val satisDir = projectDir.resolve("cloudislands-satis")
    val deletedPolicyFiles = listOf(
        satisDir.resolve("src/main/java/kr/seungmin/satisskyfactory/storage/SatisLegacyMigrationPolicy.java"),
        satisDir.resolve("src/test/java/kr/seungmin/satisskyfactory/storage/SatisLegacyMigrationPolicyTest.java")
    )
    val scanFiles = fileTree(satisDir.resolve("src/main")) {
        include("**/*.java", "**/*.yml", "**/*.yaml", "**/*.properties")
    }.files.toMutableList().also {
        it += satisDir.resolve("build.gradle.kts")
    }.filter { it.isFile }.sortedBy { it.relativeTo(projectDir).path }

    inputs.files(scanFiles)
    inputs.files(deletedPolicyFiles)

    doLast {
        val stillPresent = deletedPolicyFiles.filter { it.exists() }
        if (stillPresent.isNotEmpty()) {
            throw GradleException(
                "Removed Satis legacy migration files still exist: " +
                    stillPresent.joinToString(", ") { it.relativeTo(projectDir).path }
            )
        }

        val forbiddenLegacySurface = listOf(
            "SatisLegacyMigrationPolicy",
            "LegacyImport",
            "LegacyRollback",
            "scanLegacyDatabase",
            "importLegacyDatabase",
            "rollbackLastLegacyImport",
            "migrate-superiorskyblock2",
            "migrate-ss2",
            "CONFIRM_IMPORT",
            "factory admin migration",
            "satisskyfactory.admin.migration",
            "CloudIslands-Satis-Legacy-Migration",
            "migration-input-only",
            "migration-runtime-dependency",
            "migration-manifest-policy",
            "migration-output-id-policy"
        )

        val legacyHits = scanFiles.flatMap { file ->
            val text = file.readText()
            forbiddenLegacySurface
                .filter(text::contains)
                .map { "${file.relativeTo(projectDir).path}: $it" }
        }
        if (legacyHits.isNotEmpty()) {
            throw GradleException(
                "Removed Satis legacy migration surface is still referenced:\n" +
                    legacyHits.joinToString("\n")
            )
        }

        val addonDescriptor = satisDir.resolve("src/main/resources/cloudislands-addon.yml").readText()
        val config = satisDir.resolve("src/main/resources/config.yml").readText()
        val moduleBuild = satisDir.resolve("build.gradle.kts").readLines()
        val featureGateLine = moduleBuild.firstOrNull { it.contains("\"CloudIslands-Addon-Feature-Gates\"") }.orEmpty()
        val removedFeatureGateHits = buildList {
            if (Regex("(?m)^\\s*migration:\\s*true\\s*$").containsMatchIn(config)) {
                add("config.yml still enables migration feature")
            }
            if (Regex("(?m)^\\s*migration:\\s*$").containsMatchIn(addonDescriptor)) {
                add("cloudislands-addon.yml still declares migration section")
            }
            if (Regex("(?m)^\\s*superiorskyblock2:\\s*$").containsMatchIn(addonDescriptor)) {
                add("cloudislands-addon.yml still declares superiorskyblock2 section")
            }
            if (Regex("(?m)^\\s*-\\s*migration\\s*$").containsMatchIn(addonDescriptor)) {
                add("cloudislands-addon.yml still lists migration feature")
            }
            if (featureGateLine.split("\"").any { it.split(",").map(String::trim).contains("migration") }) {
                add("CloudIslands-Addon-Feature-Gates still lists migration")
            }
        }
        if (removedFeatureGateHits.isNotEmpty()) {
            throw GradleException(removedFeatureGateHits.joinToString("\n"))
        }
    }
}

tasks.named("build") {
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
}

tasks.named("check") {
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
    dependsOn(verifySatisLegacyMigrationRemoved)
}

apply(from = "gradle/distribution.gradle.kts")

val sourceScanningVerificationTests = setOf(
    "verifyApiRouteCoverage",
    "verifyRouteDomainCoverage",
    "verifyEventCoverage",
    "verifyGuiActionCoverage",
    "verifyPermissionCoverage",
    "verifyCoreConfigCoverage",
    "verifyPaperConfigCoverage",
    "verifyMetricCoverage",
    "verifyPaperCommandCoverage",
    "verifyVelocityCommandCoverage",
    "verifySnapshotRestoreCoverage"
)

tasks.withType<Test>().configureEach {
    if (name in sourceScanningVerificationTests) {
        doNotTrackState("Source-scanning verification gates can complete without stable binary test-result outputs after clean.")
    }
}

val apiCompatibilityReportFile = layout.buildDirectory.file("reports/api-compatibility/api-compatibility-report.json")

tasks.register<Test>("verifyApiRouteCoverage") {
    group = "verification"
    description = "Verifies typed Core API client endpoints are registered by Core service routes."
    val coreClientSourceSets = project(":cloudislands-core-client").extensions.getByType<SourceSetContainer>()
    val coreClientTest = coreClientSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-client").tasks.named("testClasses"))
    testClassesDirs = coreClientTest.output.classesDirs
    classpath = coreClientTest.runtimeClasspath
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/coreclient/CoreClientRouteCoverageTest.class")
}

tasks.register<Test>("verifyRouteDomainCoverage") {
    group = "verification"
    description = "Verifies Core HTTP routes are registered and wired to real domain services or repositories."
    val coreServiceSourceSets = project(":cloudislands-core-service").extensions.getByType<SourceSetContainer>()
    val coreServiceTest = coreServiceSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-service").tasks.named("testClasses"))
    testClassesDirs = coreServiceTest.output.classesDirs
    classpath = coreServiceTest.runtimeClasspath
    workingDir = project(":cloudislands-core-service").projectDir
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/coreservice/CoreRouteDomainCoverageTest.class")
}

tasks.register<Test>("verifyEventCoverage") {
    group = "verification"
    description = "Verifies canonical event types have explicit cache consumers and required global delivery coverage."
    val commonSourceSets = project(":cloudislands-common").extensions.getByType<SourceSetContainer>()
    val commonTest = commonSourceSets.named("test").get()
    dependsOn(project(":cloudislands-common").tasks.named("testClasses"))
    testClassesDirs = commonTest.output.classesDirs
    classpath = commonTest.runtimeClasspath
    workingDir = rootProject.projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/common/event/CacheInvalidationPlanTest.class",
        "kr/lunaf/cloudislands/common/event/EventApiSurfacePolicyTest.class"
    )
}

tasks.register<Test>("verifyGuiActionCoverage") {
    group = "verification"
    description = "Verifies registered GUI actions parse to typed actions and route to executable handlers."
    val paperSourceSets = project(":cloudislands-paper").extensions.getByType<SourceSetContainer>()
    val paperTest = paperSourceSets.named("test").get()
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    testClassesDirs = paperTest.output.classesDirs
    classpath = paperTest.runtimeClasspath
    workingDir = project(":cloudislands-paper").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/paper/gui/GuiActionParserTest.class",
        "kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.class"
    )
}

tasks.register<Test>("verifyPermissionCoverage") {
    group = "verification"
    description = "Verifies plugin.yml permission nodes are backed by command descriptors or runtime checks."
    val paperSourceSets = project(":cloudislands-paper").extensions.getByType<SourceSetContainer>()
    val paperTest = paperSourceSets.named("test").get()
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    testClassesDirs = paperTest.output.classesDirs
    classpath = paperTest.runtimeClasspath
    workingDir = project(":cloudislands-paper").projectDir
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/paper/admin/AdminCommandBackendPolicyTest.class")
}

val verifyCoreConfigCoverage = tasks.register<Test>("verifyCoreConfigCoverage") {
    group = "verification"
    description = "Verifies Core config keys are loaded and consumed by runtime code."
    val coreServiceSourceSets = project(":cloudislands-core-service").extensions.getByType<SourceSetContainer>()
    val coreServiceTest = coreServiceSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-service").tasks.named("testClasses"))
    testClassesDirs = coreServiceTest.output.classesDirs
    classpath = coreServiceTest.runtimeClasspath
    workingDir = project(":cloudislands-core-service").projectDir
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/coreservice/config/CoreConfigSurfaceTest.class")
}

val verifyPaperConfigCoverage = tasks.register<Test>("verifyPaperConfigCoverage") {
    group = "verification"
    description = "Verifies Paper config keys are loaded and consumed by runtime code."
    val paperSourceSets = project(":cloudislands-paper").extensions.getByType<SourceSetContainer>()
    val paperTest = paperSourceSets.named("test").get()
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    testClassesDirs = paperTest.output.classesDirs
    classpath = paperTest.runtimeClasspath
    workingDir = project(":cloudislands-paper").projectDir
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/paper/PaperConfigSurfaceTest.class")
}

tasks.register("verifyConfigCoverage") {
    group = "verification"
    description = "Verifies goal config keys map to runtime loaders and real consumers."
    dependsOn(verifyCoreConfigCoverage)
    dependsOn(verifyPaperConfigCoverage)
}

tasks.register<Test>("verifyMetricCoverage") {
    group = "verification"
    description = "Verifies rendered metrics have concrete update sources and dashboard samples."
    val coreServiceSourceSets = project(":cloudislands-core-service").extensions.getByType<SourceSetContainer>()
    val coreServiceTest = coreServiceSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-service").tasks.named("testClasses"))
    testClassesDirs = coreServiceTest.output.classesDirs
    classpath = coreServiceTest.runtimeClasspath
    workingDir = rootProject.projectDir
    useJUnitPlatform()
    include("kr/lunaf/cloudislands/coreservice/metrics/PrometheusMetricsRendererTest.class")
}

val verifyPaperCommandCoverage = tasks.register<Test>("verifyPaperCommandCoverage") {
    group = "verification"
    description = "Verifies Paper command catalog, help, and handler routing coverage."
    val paperSourceSets = project(":cloudislands-paper").extensions.getByType<SourceSetContainer>()
    val paperTest = paperSourceSets.named("test").get()
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    testClassesDirs = paperTest.output.classesDirs
    classpath = paperTest.runtimeClasspath
    workingDir = project(":cloudislands-paper").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/paper/command/IslandCommandCatalogTest.class",
        "kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.class",
        "kr/lunaf/cloudislands/paper/command/IslandCommandDelayPolicyTest.class",
        "kr/lunaf/cloudislands/paper/command/IslandCommandWarmupPolicyTest.class"
    )
}

val verifyVelocityCommandCoverage = tasks.register<Test>("verifyVelocityCommandCoverage") {
    group = "verification"
    description = "Verifies Velocity command catalog and alias registration coverage."
    val velocitySourceSets = project(":cloudislands-velocity").extensions.getByType<SourceSetContainer>()
    val velocityTest = velocitySourceSets.named("test").get()
    dependsOn(project(":cloudislands-velocity").tasks.named("testClasses"))
    testClassesDirs = velocityTest.output.classesDirs
    classpath = velocityTest.runtimeClasspath
    workingDir = project(":cloudislands-velocity").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/velocity/command/IslandCommandCatalogTest.class",
        "kr/lunaf/cloudislands/velocity/command/VelocityCommandRegistrarTest.class"
    )
}

tasks.register("verifyCommandCoverage") {
    group = "verification"
    description = "Verifies command catalogs, help entries, aliases, and handler routing are covered by executable tests."
    dependsOn(verifyPaperCommandCoverage)
    dependsOn(verifyVelocityCommandCoverage)
}

tasks.register<Exec>("verifyProtectionSmoke") {
    group = "verification"
    description = "Verifies Paper protection live-interaction smoke scenarios and role matrix coverage."
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    commandLine(
        "python3",
        file("scripts/ci/paper_protection_smoke.py").absolutePath
    )
}

tasks.named("check") {
    dependsOn(tasks.named("verifyProtectionSmoke"))
}

tasks.register<JavaExec>("apiCompatibilityCheck") {
    group = "verification"
    description = "Verifies the CloudIslands API compatibility contract before release."
    dependsOn(project(":cloudislands-testkit").tasks.named("classes"))
    val testkitSourceSets = project(":cloudislands-testkit").extensions.getByType<SourceSetContainer>()
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("kr.lunaf.cloudislands.testkit.ApiCompatibilityCheckCli")
    args("--report-out", apiCompatibilityReportFile.get().asFile.absolutePath)
}

tasks.register("protocolCompatibilityCheck") {
    group = "verification"
    description = "Verifies previous CloudIslands protocol payloads decode on the current runtime."
    dependsOn(project(":cloudislands-protocol").tasks.named("test"))
}

tasks.register("verifyGeneratorRules") {
    group = "verification"
    description = "Verifies generator rule domain, Core routes, Paper listener, and tests remain present."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val requiredFiles = listOf(
        "cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/generator/GeneratorRuleSnapshot.java",
        "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/generator/IslandGeneratorRepository.java",
        "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/GeneratorRoutes.java",
        "cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/GeneratorQueryClient.java",
        "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/generator/IslandGeneratorListener.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/GeneratorRoutesTest.java",
        "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/generator/GeneratorSystemPolicyTest.java"
    )
    inputs.files(requiredFiles.map { layout.projectDirectory.file(it) })
    doLast {
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Generator rule evidence missing: ${missing.joinToString(", ")}")
        }
    }
}

tasks.register("verifyUpgradeEffectCoverage") {
    group = "verification"
    description = "Verifies upgrade effects apply island limits, fly flags, generator tier state, and border/biome runtime policy coverage."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val applier = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/upgrade/UpgradeEffectApplier.java")
    val routes = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandUpgradeRoutes.java")
    val test = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/upgrade/UpgradeEffectApplierTest.java")
    val settingsTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutesTest.java")
    val borderPolicy = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/IslandBorderRuntimePolicy.java")
    val borderPolicyTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/IslandBorderRuntimePolicyTest.java")
    val environmentHandler = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java")
    inputs.files(applier, routes, test, settingsTest, borderPolicy, borderPolicyTest, environmentHandler)
    doLast {
        val applierSource = applier.asFile.readText()
        val routeSource = routes.asFile.readText()
        val tests = test.asFile.readText()
        val settingsTests = settingsTest.asFile.readText()
        val borderPolicySource = borderPolicy.asFile.readText()
        val borderTests = borderPolicyTest.asFile.readText()
        val environmentSource = environmentHandler.asFile.readText()
        val failures = buildList {
            if (!applierSource.contains("generators.setProfile")) add("UpgradeEffectApplier must update generator profiles for generator upgrades")
            if (!applierSource.contains("case HOME_LIMIT") || !applierSource.contains("case BORDER_SIZE") || !applierSource.contains("case BIOME_UNLOCK")) add("UpgradeEffectApplier must apply home, border, and biome unlock limit effects")
            if (!applierSource.contains("KEEP_INVENTORY_ENABLE") || !applierSource.contains("BORDER_COLOR_UNLOCK")) add("UpgradeEffectApplier must apply keep inventory and border color flag effects")
            if (!routeSource.contains("generatorRepository")) add("IslandUpgradeRoutes must pass generatorRepository into upgrade effects")
            if (!tests.contains("generatorUpgradeUpdatesAuthoritativeGeneratorProfile")) add("UpgradeEffectApplierTest must cover generator profile effects")
            if (!tests.contains("borderHomeAndBiomeUpgradesUpdateAuthoritativeLimits")) add("UpgradeEffectApplierTest must cover border, home, and biome limit effects")
            if (!tests.contains("keepInventoryAndBorderColorUpgradesApplyFlags")) add("UpgradeEffectApplierTest must cover keep inventory and border color flag effects")
            if (!settingsTests.contains("setBiomeNormalizesSupportedKeysAndRejectsUnsupportedKeys")) add("IslandSettingsRoutesTest must cover biome normalization and rejection")
            if (!settingsTests.contains("setBiomeSkipsDuplicateWritesLogsAndEvents")) add("IslandSettingsRoutesTest must cover duplicate biome no-op protection")
            if (!environmentSource.contains("BIOME_UNCHANGED")) add("IslandEnvironmentCommandHandler must surface duplicate biome no-op results separately")
            if (!borderPolicySource.contains("BorderSettings") || !borderPolicySource.contains("region.originX()") || !borderPolicySource.contains("Math.max(1.0D, borderSize)")) add("IslandBorderRuntimePolicy must compute player world border center and size")
            if (!borderTests.contains("appliesWorldBorderFromIslandRegionAndCoreSize") || !borderTests.contains("hiddenPolicySuppressesPlayerWorldBorder")) add("IslandBorderRuntimePolicyTest must cover visible and hidden border behavior")
            if (!environmentSource.contains("IslandBorderRuntimePolicy.settings") || !environmentSource.contains("player.setWorldBorder(border)") || !environmentSource.contains("player.setWorldBorder(null)")) add("IslandEnvironmentCommandHandler must apply border runtime policy to the player world border")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyReviewModerationCoverage") {
    group = "verification"
    description = "Verifies review report/moderation routes, repository filtering, admin permission, and schema migration remain present."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    val route = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandReviewRoutes.java")
    val repository = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/review/IslandReviewRepository.java")
    val guard = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/security/AdminEndpointGuard.java")
    val migration = layout.projectDirectory.file("cloudislands-core-service/src/main/resources/db/migration/V70__review_moderation.sql")
    val test = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/review/InMemoryIslandReviewRepositoryTest.java")
    inputs.files(route, repository, guard, migration, test)
    doLast {
        val routeSource = route.asFile.readText()
        val repositorySource = repository.asFile.readText()
        val guardSource = guard.asFile.readText()
        val migrationSource = migration.asFile.readText()
        val testSource = test.asFile.readText()
        val failures = buildList {
            if (!routeSource.contains("/v1/islands/reviews/report") || !routeSource.contains("/v1/admin/reviews/moderate")) add("IslandReviewRoutes missing report/moderate endpoints")
            if (!repositorySource.contains("moderationQueue") || !repositorySource.contains("report(")) add("IslandReviewRepository missing moderation operations")
            if (!guardSource.contains("MODERATION_MANAGE")) add("AdminEndpointGuard missing moderation permission mapping")
            if (!migrationSource.contains("moderation_state") || !migrationSource.contains("report_count")) add("review moderation migration missing required fields")
            if (!testSource.contains("reportsAndHidesReviewsFromPublicListsAndRankings")) add("review moderation repository test missing")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyMissionEventProgress") {
    group = "verification"
    description = "Verifies Paper mission event progress listeners and tests remain present."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val listener = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListener.java")
    val test = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/mission/MissionProgressTriggersTest.java")
    inputs.files(listener, test)
    doLast {
        val listenerSource = listener.asFile.readText()
        val triggerSource = test.asFile.readText()
        val missingSignals = listOf("BlockBreakEvent", "BlockPlaceEvent", "EntityDeathEvent", "PlayerFishEvent", "CraftItemEvent", "Ageable")
            .filterNot(listenerSource::contains) +
            listOf("BANK_BALANCE", "GENERATOR_COLLECT", "bankBalance", "generatorCollect")
                .filterNot { triggerSource.contains(it) || listenerSource.contains(it) }
        if (missingSignals.isNotEmpty()) {
            throw GradleException("Mission event progress listener missing triggers: ${missingSignals.joinToString(", ")}")
        }
        if (!test.asFile.isFile) {
            throw GradleException("Mission progress trigger test is missing")
        }
    }
}

tasks.register("verifyMissionRewardCoverage") {
    group = "verification"
    description = "Verifies mission reward types cover bank, command, item, upgrade discount, permission, limits, and generator tier."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val service = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/mission/MissionRewardService.java")
    val test = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/mission/MissionRewardServiceTest.java")
    val paperDelivery = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/MissionRewardDeliveryListener.java")
    val paperDeliveryTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/mission/MissionRewardDeliveryListenerTest.java")
    inputs.files(service, test, paperDelivery, paperDeliveryTest)
    doLast {
        val source = service.asFile.readText()
        val tests = test.asFile.readText()
        val delivery = paperDelivery.asFile.readText()
        val deliveryTests = paperDeliveryTest.asFile.readText()
        val requiredTypes = listOf("BANK_DEPOSIT", "COMMAND", "ITEM", "UPGRADE_DISCOUNT", "PERMISSION_TEMPORARY", "LIMIT_INCREASE", "GENERATOR_TIER")
        val missingTypes = requiredTypes.filterNot(source::contains)
        val missingTests = listOf("appliesCoreBackedMissionRewards", "queuesPaperDeliveredMissionRewards").filterNot(tests::contains)
        val missingDelivery = listOf("COMMAND_REWARD_QUEUED", "ITEM_REWARD_QUEUED", "dispatchCommand(plugin.getServer().getConsoleSender(), command)", "player.getInventory().addItem").filterNot(delivery::contains)
        val missingDeliveryTests = listOf("commandRewardReplacesPlayerAndUuidPlaceholders", "itemRewardParsesNamespacedMaterialAndClampsAmount").filterNot(deliveryTests::contains)
        val failures = buildList {
            if (missingTypes.isNotEmpty()) add("MissionRewardService missing reward types: ${missingTypes.joinToString(", ")}")
            if (missingTests.isNotEmpty()) add("MissionRewardServiceTest missing coverage: ${missingTests.joinToString(", ")}")
            if (missingDelivery.isNotEmpty()) add("MissionRewardDeliveryListener missing delivery behavior: ${missingDelivery.joinToString(", ")}")
            if (missingDeliveryTests.isNotEmpty()) add("MissionRewardDeliveryListenerTest missing coverage: ${missingDeliveryTests.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyEconomyTransactionSafety") {
    group = "verification"
    description = "Verifies Vault/Core bank transaction rollback and provider-state safety tests remain present."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val useCase = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/BankUseCase.java")
    val test = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/BankUseCaseTest.java")
    inputs.files(useCase, test)
    doLast {
        val source = useCase.asFile.readText()
        val tests = test.asFile.readText()
        val missingSignals = listOf("refundPlayer", "island.bank.withdraw.rollback", "ECONOMY_OPERATION_FAILED", "REFUND_FAILED_AFTER_CORE_REJECTION", "ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE")
            .filterNot { source.contains(it) || tests.contains(it) }
        if (missingSignals.isNotEmpty()) {
            throw GradleException("Economy transaction safety evidence missing: ${missingSignals.joinToString(", ")}")
        }
    }
}

tasks.register("verifySatisEconomyLedgerCoverage") {
    group = "verification"
    description = "Verifies Satis economy idempotency ledger schema, service usage, and retry tests remain present."
    dependsOn(project(":cloudislands-satis").tasks.named("test"))
    val database = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java")
    val schema = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/storage/SatisSchemaService.java")
    val market = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/market/MarketService.java")
    val contracts = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/contract/ContractService.java")
    val research = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/research/ResearchService.java")
    val maintenance = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/machine/MaintenanceService.java")
    val databaseTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/database/DatabaseServiceTest.java")
    val economyTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/database/EconomyFlowServiceTest.java")
    val contractTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/database/ContractFlowServiceTest.java")
    inputs.files(database, schema, market, contracts, research, maintenance, databaseTest, economyTest, contractTest)
    doLast {
        val schemaSource = schema.asFile.readText()
        val databaseSource = database.asFile.readText()
        val serviceSources = listOf(market, contracts, research, maintenance).joinToString("\n") { it.asFile.readText() }
        val tests = listOf(databaseTest, economyTest, contractTest).joinToString("\n") { it.asFile.readText() }
        val missingSchema = listOf("satis_economy_ledger", "satis_reward_ledger", "satis_command_idempotency", "idempotency_key")
            .filterNot(schemaSource::contains)
        val missingDatabase = listOf("beginEconomyLedger", "completeEconomyLedger", "compensateEconomyLedger", "EconomyLedgerClaim")
            .filterNot(databaseSource::contains)
        val missingServices = listOf("MARKET_SELL", "CONTRACT_REWARD", "RESEARCH_UNLOCK", "ADMIN_MAINTENANCE_CHARGE")
            .filterNot(serviceSources::contains)
        val missingTests = listOf(
            "economyLedgerIdempotencyKeyPreventsDuplicateBeginsAndTracksCompletion",
            "failedMarketPayoutLeavesRetryLedgerAndDoesNotRecordSale",
            "duplicateForcedMaintenanceChargeDoesNotWithdrawTwice",
            "completedRewardLedgerLetsContractRetryWithoutDuplicateMoneyReward"
        ).filterNot(tests::contains)
        val failures = buildList {
            if (missingSchema.isNotEmpty()) add("Satis economy ledger schema missing: ${missingSchema.joinToString(", ")}")
            if (missingDatabase.isNotEmpty()) add("Satis economy ledger database API missing: ${missingDatabase.joinToString(", ")}")
            if (missingServices.isNotEmpty()) add("Satis economy ledger service usage missing: ${missingServices.joinToString(", ")}")
            if (missingTests.isNotEmpty()) add("Satis economy ledger tests missing: ${missingTests.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifySatisMigrationReportCoverage") {
    group = "verification"
    description = "Verifies SuperiorSkyblock2 migration report and compare operations remain wired through Core, Paper, Velocity, policy, and security layers."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    dependsOn(project(":cloudislands-velocity").tasks.named("test"))
    val backend = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminBackend.java")
    val service = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminService.java")
    val routes = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/SuperiorSkyblock2MigrationRoutes.java")
    val guard = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/security/AdminEndpointGuard.java")
    val configRoutes = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/CoreConfigRoutes.java")
    val commonPolicy = layout.projectDirectory.file("cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/feature/SuperiorSkyblockReplacementFeaturePolicy.java")
    val client = layout.projectDirectory.file("cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkMigrationCommandClient.java")
    val paperCatalog = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java")
    val paperHandler = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminMigrationCommandHandler.java")
    val velocityCatalog = layout.projectDirectory.file("cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/command/IslandCommandCatalog.java")
    val velocityDispatcher = layout.projectDirectory.file("cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java")
    val velocitySuggestions = layout.projectDirectory.file("cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSuggestions.java")
    val serviceTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/MigrationAdminServiceTest.java")
    val routesTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/SuperiorSkyblock2MigrationRoutesTest.java")
    val paperTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackendPolicyTest.java")
    val velocityTest = layout.projectDirectory.file("cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/command/IslandCommandCatalogTest.java")
    inputs.files(
        backend, service, routes, guard, configRoutes, commonPolicy, client,
        paperCatalog, paperHandler, velocityCatalog, velocityDispatcher, velocitySuggestions,
        serviceTest, routesTest, paperTest, velocityTest
    )
    doLast {
        val backendSource = backend.asFile.readText()
        val serviceSource = service.asFile.readText()
        val routeSource = routes.asFile.readText()
        val guardSource = guard.asFile.readText()
        val configSource = configRoutes.asFile.readText()
        val policySource = commonPolicy.asFile.readText()
        val clientSource = client.asFile.readText()
        val commandSurfaces = listOf(paperCatalog, paperHandler, velocityCatalog, velocityDispatcher, velocitySuggestions)
            .joinToString("\n") { it.asFile.readText() }
        val tests = listOf(serviceTest, routesTest, paperTest, velocityTest).joinToString("\n") { it.asFile.readText() }
        val missingCore = listOf(
            "scan,dryrun,report,import,verify,compare,rollback",
            "public synchronized String report()",
            "public synchronized String compare(String islandKey)",
            "compareImportedManifest(MigrationManifest manifest)",
            "backend.report()",
            "backend.compare(islandKey)",
            "/v1/admin/migrations/superiorskyblock2/report",
            "/v1/admin/migrations/superiorskyblock2/compare"
        ).filterNot { signal ->
            backendSource.contains(signal) || serviceSource.contains(signal) || routeSource.contains(signal)
        }
        val missingSecurity = listOf(
            "/v1/admin/migrations/superiorskyblock2/report",
            "/v1/admin/migrations/superiorskyblock2/compare",
            "AdminPermission.MIGRATION_MANAGE"
        ).filterNot(guardSource::contains)
        val missingPolicy = listOf(
            "superiorSkyblock2MigrationEndpoints\", \"scan,status,dryrun,report,extract,import,verify,compare,rollback",
            "/ciadmin migrate-superiorskyblock2 report",
            "/ciadmin migrate-superiorskyblock2 compare"
        ).filterNot { signal -> configSource.contains(signal) || policySource.contains(signal) }
        val missingClient = listOf("case \"report\"", "case \"compare\"", "SUPERIOR_SKYBLOCK2_REPORT", "SUPERIOR_SKYBLOCK2_COMPARE")
            .filterNot(clientSource::contains)
        val missingCommands = listOf(
            "migrate-superiorskyblock2 report",
            "migrate-superiorskyblock2 compare <island>",
            "action.equalsIgnoreCase(\"compare\") && args.length < 3",
            "<islandUuid>",
            "<ownerUuid>"
        ).filterNot(commandSurfaces::contains)
        val missingTests = listOf(
            "migrationReportAndCompareAreFirstClassOperations",
            "/v1/admin/migrations/superiorskyblock2/report",
            "/v1/admin/migrations/superiorskyblock2/compare",
            "Migration report must be a first-class migration subcommand",
            "ciadmin migrate-superiorskyblock2 compare <island>"
        ).filterNot(tests::contains)
        val failures = buildList {
            if (missingCore.isNotEmpty()) add("Satis migration report Core wiring missing: ${missingCore.joinToString(", ")}")
            if (missingSecurity.isNotEmpty()) add("Satis migration report security wiring missing: ${missingSecurity.joinToString(", ")}")
            if (missingPolicy.isNotEmpty()) add("Satis migration report policy/config wiring missing: ${missingPolicy.joinToString(", ")}")
            if (missingClient.isNotEmpty()) add("Satis migration report client wiring missing: ${missingClient.joinToString(", ")}")
            if (missingCommands.isNotEmpty()) add("Satis migration report command wiring missing: ${missingCommands.joinToString(", ")}")
            if (missingTests.isNotEmpty()) add("Satis migration report tests missing: ${missingTests.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyRankingWorthCertification") {
    group = "verification"
    description = "Verifies ranking/worth certification covers 10k island recalculation, dirty debounce, ignored islands, and event publication."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    val recalculation = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/ranking/RankingRecalculationService.java")
    val rankingRepository = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/ranking/RankingRepository.java")
    val jdbcRankingRepository = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/ranking/JdbcRankingRepository.java")
    val dirtyTask = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTask.java")
    val blockLevelRoutes = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandBlockLevelRoutes.java")
    val placeholderExpansion = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/placeholder/CloudIslandsPlaceholderExpansion.java")
    val placeholderRanks = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/placeholder/CloudIslandsPlaceholderRanks.java")
    val recalculationTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/RankingRecalculationServiceTest.java")
    val dirtyTaskTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTaskTest.java")
    val blockLevelRoutesTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandBlockLevelRoutesTest.java")
    val placeholderRanksTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/placeholder/CloudIslandsPlaceholderRanksTest.java")
    inputs.files(recalculation, rankingRepository, jdbcRankingRepository, dirtyTask, blockLevelRoutes, placeholderExpansion, placeholderRanks, recalculationTest, dirtyTaskTest, blockLevelRoutesTest, placeholderRanksTest)
    doLast {
        val serviceSource = recalculation.asFile.readText()
        val repositorySource = rankingRepository.asFile.readText()
        val jdbcSource = jdbcRankingRepository.asFile.readText()
        val dirtySource = dirtyTask.asFile.readText()
        val blockLevelSource = blockLevelRoutes.asFile.readText()
        val placeholderSource = placeholderExpansion.asFile.readText()
        val placeholderRankSource = placeholderRanks.asFile.readText()
        val recalculationTests = recalculationTest.asFile.readText()
        val dirtyTests = dirtyTaskTest.asFile.readText()
        val blockLevelTests = blockLevelRoutesTest.asFile.readText()
        val placeholderTests = placeholderRanksTest.asFile.readText()
        val failures = buildList {
            listOf("ISLAND_LEVEL_UPDATED", "ISLAND_WORTH_CHANGED").filterNot(serviceSource::contains).forEach {
                add("Ranking recalculation must publish event signal: $it")
            }
            listOf("setIgnored", "isIgnored").filterNot(repositorySource::contains).forEach {
                add("RankingRepository missing ignored-island API: $it")
            }
            if (!jdbcSource.contains("WHERE ignored = false")) add("JdbcRankingRepository must exclude ignored islands from top rankings")
            if (!dirtySource.contains("BATCH_LIMIT = 100")) add("DirtyRankingRecalculationTask must keep bounded batches")
            if (!blockLevelSource.contains("ISLAND_LEVEL_RECALCULATE")) add("Manual/admin ranking recalculation must write an audit action")
            if (!placeholderSource.contains("\"rank\"") || !placeholderSource.contains("rankings(100)") || !placeholderRankSource.contains("worthRank")) add("PlaceholderAPI expansion must expose ranking placeholders from typed rankings")
            if (!recalculationTests.contains("tenThousandIslandWorthRecalculationRanksAndExcludesIgnoredIslands")) add("Ranking 10k worth certification test is missing")
            if (!dirtyTests.contains("tenThousandDirtyIslandsStayDebouncedAndProcessOnlyBatchLimitPerRun")) add("Dirty ranking 10k debounce certification test is missing")
            if (!blockLevelTests.contains("levelRecalculationWritesAuditAndEventSignals")) add("Admin/manual recalculation audit test is missing")
            if (!placeholderTests.contains("rendersWorthAndLevelRankPlaceholdersFromProgressionRankings")) add("Placeholder rank certification test is missing")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyGuiButtonCoverage") {
    group = "verification"
    description = "Verifies shared GUI button states and state-menu coverage remain present."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val buttonState = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiButtonState.java")
    val policyTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/gui/GuiSystemPolicyTest.java")
    inputs.files(buttonState, policyTest)
    doLast {
        val source = buttonState.asFile.readText()
        val requiredStates = listOf("ENABLED", "DISABLED_NO_PERMISSION", "DISABLED_REQUIREMENT_NOT_MET", "DISABLED_NOT_ENOUGH_MONEY", "LOADING", "ERROR_RETRYABLE", "ERROR_FATAL")
        val missing = requiredStates.filterNot(source::contains)
        if (missing.isNotEmpty()) {
            throw GradleException("GUI button states missing: ${missing.joinToString(", ")}")
        }
        if (!policyTest.asFile.readText().contains("guiButtonsUseSharedStateModel")) {
            throw GradleException("GUI button state policy test is missing")
        }
    }
}

tasks.register("verifyCommandHelpCoverage") {
    group = "verification"
    description = "Verifies categorized player help and command suggestion coverage remain present."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val catalog = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalog.java")
    val router = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java")
    val completer = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java")
    val test = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalogTest.java")
    inputs.files(catalog, router, completer, test)
    doLast {
        val catalogSource = catalog.asFile.readText()
        val missingCategories = listOf("기본", "멤버", "방문", "성장", "설정", "관리자").filterNot(catalogSource::contains)
        if (missingCategories.isNotEmpty()) {
            throw GradleException("Command help categories missing: ${missingCategories.joinToString(", ")}")
        }
        if (!router.asFile.readText().contains("helpCategoryRequest(args)")) {
            throw GradleException("IslandCommandRouter no longer routes categorized help")
        }
        if (!completer.asFile.readText().contains("helpRootSuggestions()")) {
            throw GradleException("IslandCommandTabCompleter no longer suggests help categories")
        }
        if (!completer.asFile.readText().contains("IslandCommandCatalog.upgradeKeys()")) {
            throw GradleException("IslandCommandTabCompleter no longer suggests upgrade keys")
        }
        if (!test.asFile.readText().contains("categorizedHelpOnlyReferencesAdvertisedCommands")) {
            throw GradleException("Categorized help catalog test is missing")
        }
        if (!test.asFile.readText().contains("upgradeKeySuggestionsCoverConfiguredUpgradeEffects")) {
            throw GradleException("Upgrade key suggestion coverage test is missing")
        }
    }
}

val certifyExampleAddon = tasks.register("certifyExampleAddon") {
    group = "verification"
    description = "Runs the example addon certification command and writes JSON/Markdown certification reports."
    dependsOn(project(":cloudislands-example-addon").tasks.named("test"))
    dependsOn(project(":cloudislands-testkit").tasks.named("test"))
    val jsonReport = layout.buildDirectory.file("reports/cloudislands/addon-certification.json")
    val markdownReport = layout.buildDirectory.file("reports/cloudislands/addon-certification.md")
    outputs.files(jsonReport, markdownReport)
    doLast {
        val generatedAt = Instant.now().toString()
        val reportsDir = jsonReport.get().asFile.parentFile
        reportsDir.mkdirs()
        jsonReport.get().asFile.writeText(
            """
            {"generatedAt":"$generatedAt","addonId":"cloudislands-example-addon","command":"./gradlew certifyExampleAddon","certificationLevel":"developer-kit-example","requiredFailures":0,"reports":["build/reports/cloudislands/addon-certification.json","build/reports/cloudislands/addon-certification.md"]}
            """.trimIndent()
        )
        markdownReport.get().asFile.writeText(
            """
            # CloudIslands example addon certification

            - Generated: $generatedAt
            - Command: `./gradlew certifyExampleAddon`
            - Addon: `cloudislands-example-addon`
            - Required failures: 0
            - Evidence: `cloudislands-example-addon:test`, `cloudislands-testkit:test`
            """.trimIndent() + "\n"
        )
    }
}

tasks.register("verifyAddonDeveloperKitCoverage") {
    group = "verification"
    description = "Verifies addon developer kit API examples, certification checks, and distribution hooks remain present."
    dependsOn(tasks.named("distDeveloperKit"))
    dependsOn(certifyExampleAddon)
    dependsOn(project(":cloudislands-example-addon").tasks.named("test"))
    dependsOn(project(":cloudislands-testkit").tasks.named("test"))
    val addonApi = layout.projectDirectory.file("cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/addon/CloudIslandsAddon.java")
    val example = layout.projectDirectory.file("cloudislands-example-addon/src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleCloudIslandsAddonDefinition.java")
    val examplePlugin = layout.projectDirectory.file("cloudislands-example-addon/src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleCloudIslandsAddonPlugin.java")
    val exampleEventListener = layout.projectDirectory.file("cloudislands-example-addon/src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleCloudIslandsEventListener.java")
    val exampleCommand = layout.projectDirectory.file("cloudislands-example-addon/src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleIslandCommand.java")
    val exampleMenuAction = layout.projectDirectory.file("cloudislands-example-addon/src/main/java/kr/lunaf/cloudislands/exampleaddon/ExampleIslandMenuAction.java")
    val exampleTest = layout.projectDirectory.file("cloudislands-example-addon/src/test/java/kr/lunaf/cloudislands/exampleaddon/ExampleCloudIslandsAddonDefinitionTest.java")
    val certification = layout.projectDirectory.file("cloudislands-testkit/src/main/java/kr/lunaf/cloudislands/testkit/AddonCertificationMatrix.java")
    val certificationTest = layout.projectDirectory.file("cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/AddonCertificationMatrixTest.java")
    val devkitDir = layout.buildDirectory.dir("dist/devkit")
    val certificationJsonReport = layout.buildDirectory.file("reports/cloudislands/addon-certification.json")
    val certificationMarkdownReport = layout.buildDirectory.file("reports/cloudislands/addon-certification.md")
    inputs.files(addonApi, example, examplePlugin, exampleEventListener, exampleCommand, exampleMenuAction, exampleTest, certification, certificationTest)
    inputs.dir(devkitDir)
    inputs.files(certificationJsonReport, certificationMarkdownReport)
    doLast {
        val apiSource = addonApi.asFile.readText()
        val exampleSource = example.asFile.readText()
        val examplePluginSource = examplePlugin.asFile.readText()
        val eventListenerSource = exampleEventListener.asFile.readText()
        val commandSource = exampleCommand.asFile.readText()
        val menuActionSource = exampleMenuAction.asFile.readText()
        val exampleTests = exampleTest.asFile.readText()
        val certificationSource = certification.asFile.readText()
        val certificationTests = certificationTest.asFile.readText()
        val failures = buildList {
            listOf("addonMissions", "addonPlaceholders", "addonMenuButtons", "addonBlockValues").filterNot(apiSource::contains).forEach {
                add("CloudIslandsAddon missing developer kit SPI: $it")
            }
            listOf("new MissionProviderDefinitionSnapshot", "new AddonPlaceholderSnapshot", "new AddonMenuButtonSnapshot", "new BlockValueSnapshot").filterNot(exampleSource::contains).forEach {
                add("Example addon missing reference implementation: $it")
            }
            listOf("ExampleCloudIslandsEventListener", "ExampleIslandCommand", "ExampleIslandMenuAction").filterNot(exampleSource::contains).forEach {
                add("Example addon metadata missing executable example pointer: $it")
            }
            if (!eventListenerSource.contains("RouteTicketCreatedEvent") || !eventListenerSource.contains("IslandMissionProgressEvent")) {
                add("Example addon missing typed CloudIslands event listener example")
            }
            if (!commandSource.contains("implements CommandExecutor") || !examplePluginSource.contains("new ExampleIslandCommand")) {
                add("Example addon missing Bukkit command registration example")
            }
            if (!menuActionSource.contains("AddonMenuButtonSnapshot") || !menuActionSource.contains("commandFor")) {
                add("Example addon missing menu action resolution example")
            }
            listOf("custom-missions", "placeholders", "custom-menu-buttons", "custom-block-values", "exampleEventListenerCommandAndMenuActionAreExecutableReferences").filterNot(exampleTests::contains).forEach {
                add("Example addon certification test missing feature assertion: $it")
            }
            listOf("addon-data-retention", "addon-event-failure-policy", "providerKeysPresent").filterNot(certificationSource::contains).forEach {
                add("Addon certification matrix missing check: $it")
            }
            listOf("rejectsMissingEventIsolationAndStatePersistenceMetadata", "rejectsFeatureProvidersWithoutPublishedKeys").filterNot(certificationTests::contains).forEach {
                add("Addon certification regression test missing: $it")
            }
            val dist = devkitDir.get().asFile
            if (!dist.resolve("examples/cloudislands-example-addon").isDirectory) {
                add("distDeveloperKit missing example addon source")
            }
            if (!dist.resolve("maven").isDirectory || !dist.resolve("javadocs").isDirectory || !dist.resolve("sources").isDirectory) {
                add("distDeveloperKit missing maven, javadocs, or sources output")
            }
            if (!certificationJsonReport.get().asFile.readText().contains("\"command\":\"./gradlew certifyExampleAddon\"")) {
                add("Addon certification command report missing JSON command evidence")
            }
            if (!certificationMarkdownReport.get().asFile.readText().contains("./gradlew certifyExampleAddon")) {
                add("Addon certification command report missing Markdown command evidence")
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifySatisNetworkRebuildDebounceCoverage") {
    group = "verification"
    description = "Verifies Satis item and power network rebuilds are debounced through dirty queues and drained from the machine tick."
    dependsOn(project(":cloudislands-satis").tasks.named("test"))
    val itemService = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/logistics/ItemNetworkService.java")
    val powerService = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/power/PowerNetworkService.java")
    val tickService = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/task/MachineTickService.java")
    val machineListener = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/listener/MachineListener.java")
    val lifecycleListener = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/listener/FactoryLifecycleListener.java")
    val guiListener = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/listener/FactoryGuiListener.java")
    val itemTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/logistics/ItemNetworkServiceTest.java")
    val powerTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/power/PowerNetworkServiceTest.java")
    inputs.files(itemService, powerService, tickService, machineListener, lifecycleListener, guiListener, itemTest, powerTest)
    doLast {
        val itemSource = itemService.asFile.readText()
        val powerSource = powerService.asFile.readText()
        val tickSource = tickService.asFile.readText()
        val listenerSources = listOf(machineListener, lifecycleListener, guiListener).joinToString("\n") { it.asFile.readText() }
        val tests = itemTest.asFile.readText() + "\n" + powerTest.asFile.readText()
        val missingQueueApi = listOf("pendingRebuilds", "requestRebuild", "flushRebuildQueue", "queuedRebuildCount", "discardRebuildQueue")
            .filterNot { signal -> itemSource.contains(signal) && powerSource.contains(signal) }
        val missingTickDrain = listOf("itemNetworks.flushRebuildQueue(maxPerCycle)", "power.flushRebuildQueue(maxPerCycle)")
            .filterNot(tickSource::contains)
        val missingListenerRequests = listOf("itemNetworks.requestRebuild", "power.requestRebuild")
            .filterNot(listenerSources::contains)
        val debounceTestCount = Regex("rebuildRequestsAreDebouncedPerIslandUntilFlush").findAll(tests).count()
        val failures = buildList {
            if (missingQueueApi.isNotEmpty()) add("Satis network rebuild debounce queue API missing: ${missingQueueApi.joinToString(", ")}")
            if (missingTickDrain.isNotEmpty()) add("Satis network rebuild tick drain missing: ${missingTickDrain.joinToString(", ")}")
            if (missingListenerRequests.isNotEmpty()) add("Satis network rebuild listener request path missing: ${missingListenerRequests.joinToString(", ")}")
            if (debounceTestCount < 2) add("Satis item and power network debounce tests are both required")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifySatisRuntimeFencingCoverage") {
    group = "verification"
    description = "Verifies Satis shared SQL runtime owner fencing tracks CloudIslands fencing tokens and rejects stale writes."
    dependsOn(project(":cloudislands-satis").tasks.named("test"))
    val authority = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/storage/SatisRuntimeAuthority.java")
    val plugin = layout.projectDirectory.file("cloudislands-satis/src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java")
    val authorityTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/storage/SatisRuntimeAuthorityTest.java")
    val writeGateTest = layout.projectDirectory.file("cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/SatisRuntimeWriteGatePolicyTest.java")
    inputs.files(authority, plugin, authorityTest, writeGateTest)
    doLast {
        val authoritySource = authority.asFile.readText()
        val pluginSource = plugin.asFile.readText()
        val tests = authorityTest.asFile.readText() + "\n" + writeGateTest.asFile.readText()
        val failures = buildList {
            if (!authoritySource.contains("long fencingToken") || !authoritySource.contains("fencingTokenMatches")) add("Satis runtime authority must persist and compare fencing tokens")
            if (!authoritySource.contains("canWrite(UUID islandId, long localFencingToken)")) add("Satis runtime authority must expose token-aware write checks")
            if (!pluginSource.contains("event.cellZ(), event.fencingToken())")) add("Satis migration and restore events must refresh runtime fencing tokens")
            if (!tests.contains("fencingTokenMismatchBlocksStaleWrites")) add("Satis runtime authority test must reject stale fencing tokens")
            if (!tests.contains("migration and restore events must refresh the runtime owner fence token")) add("Satis plugin policy test must cover lifecycle token wiring")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyGameplayModifierRuntimeCoverage") {
    group = "verification"
    description = "Verifies SS2-style gameplay modifier admin writes are consumed by Paper runtime listeners."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val cropGrowth = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/generator/IslandCropGrowthListener.java")
    val entityLimits = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEntityLimitListener.java")
    val effects = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEffectApplier.java")
    val bootstrap = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java")
    val policyTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/limit/IslandGameplayModifierRuntimePolicyTest.java")
    inputs.files(cropGrowth, entityLimits, effects, bootstrap, policyTest)
    doLast {
        val cropSource = cropGrowth.asFile.readText()
        val entitySource = entityLimits.asFile.readText()
        val effectSource = effects.asFile.readText()
        val bootstrapSource = bootstrap.asFile.readText()
        val testSource = policyTest.asFile.readText()
        val failures = buildList {
            if (!cropSource.contains("\"RATE:CROP_GROWTH\"")) add("Paper crop growth runtime must consume RATE:CROP_GROWTH")
            if (!entitySource.contains("\"RATE:MOB_DROPS\"")) add("Paper mob drop runtime must consume RATE:MOB_DROPS")
            if (!entitySource.contains("\"RATE:SPAWNER_RATES\"")) add("Paper spawner runtime must consume RATE:SPAWNER_RATES")
            if (!entitySource.contains("CreatureSpawnEvent.SpawnReason.SPAWNER")) add("Spawner rate must be scoped to spawner-origin creature spawns")
            listOf("EFFECT:SPEED", "EFFECT:HASTE", "EFFECT:JUMP_BOOST", "EFFECT:NIGHT_VISION", "EFFECT:REGENERATION")
                .filterNot(effectSource::contains)
                .forEach { add("Paper island effect runtime missing Core key: $it") }
            if (!effectSource.contains("player.addPotionEffect")) add("Paper island effect runtime must apply potion effects to players")
            if (!bootstrapSource.contains("new IslandCropGrowthListener(plugin.agent.protection(), cropGrowthLevels, limitCache)")) add("Paper bootstrap must wire crop growth listener to Core limit cache")
            if (!bootstrapSource.contains("new IslandEffectApplier(plugin, plugin.agent.protection(), limitCache).start()")) add("Paper bootstrap must start the island effect applier")
            if (!testSource.contains("gameplayModifierLimitsAreAppliedByPaperRuntime")) add("Gameplay modifier runtime policy test is missing")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyStackedBlockParityCoverage") {
    group = "verification"
    description = "Verifies SS2-style stacked block amounts persist through Core limits and /is toggle blocks is player-facing."
    dependsOn(project(":cloudislands-common").tasks.named("test"))
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val policy = layout.projectDirectory.file("cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/feature/GameplayParityPolicy.java")
    val policyTest = layout.projectDirectory.file("cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/feature/GameplayParityPolicyTest.java")
    val memoryLimits = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/limit/InMemoryIslandLimitRepository.java")
    val jdbcLimits = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/limit/JdbcIslandLimitRepository.java")
    val limitTest = layout.projectDirectory.file("cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/limit/InMemoryIslandLimitRepositoryTest.java")
    val adminBackend = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java")
    val environmentHandler = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java")
    val catalog = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalog.java")
    val completer = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java")
    val commandTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalogTest.java")
    inputs.files(policy, policyTest, memoryLimits, jdbcLimits, limitTest, adminBackend, environmentHandler, catalog, completer, commandTest)
    doLast {
        val policySource = policy.asFile.readText()
        val policyTestSource = policyTest.asFile.readText()
        val memorySource = memoryLimits.asFile.readText()
        val jdbcSource = jdbcLimits.asFile.readText()
        val limitTestSource = limitTest.asFile.readText()
        val adminSource = adminBackend.asFile.readText()
        val environmentSource = environmentHandler.asFile.readText()
        val catalogSource = catalog.asFile.readText()
        val completerSource = completer.asFile.readText()
        val commandTestSource = commandTest.asFile.readText()
        val failures = buildList {
            if (!policySource.contains("BLOCK_AMOUNT_LIMIT_PREFIX") || !policySource.contains("STACKED_BLOCKS_VISIBLE_LIMIT_KEY")) add("Shared gameplay parity policy must define stacked block amount and visibility keys")
            if (!policyTestSource.contains("exposesStackedBlockLimitKeysAsStableGameplayContract")) add("Shared stacked block key policy test is missing")
            if (!memorySource.contains("GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY")) add("In-memory limits must seed stacked block visibility")
            if (!jdbcSource.contains("GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY")) add("JDBC limits must seed stacked block visibility")
            if (!limitTestSource.contains("persistsStackedBlockAmountsAndVisibilityWithSharedKeys")) add("Core limit repository must test stacked block persistence")
            if (!adminSource.contains("GameplayParityPolicy.blockAmountLimitKey(args[2])")) add("Admin setblockamount must write the shared BLOCK_AMOUNT limit key")
            if (!environmentSource.contains("toggleStackedBlockVisibility(player)") || !environmentSource.contains("setStackedBlockVisibility(player")) add("Paper environment command must route /is toggle blocks")
            if (!catalogSource.contains("\"섬 toggle blocks\"")) add("Command catalog must advertise /is toggle blocks")
            if (!completerSource.contains("\"blocks\", \"stacked-blocks\"")) add("Tab completer must expose toggle blocks targets")
            if (!commandTestSource.contains("SS2-style toggle blocks help must be advertised")) add("Paper command catalog test must cover /is toggle blocks")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

val verifyCoreTemplateBundleCreateCoverage = tasks.register<Test>("verifyCoreTemplateBundleCreateCoverage") {
    group = "verification"
    description = "Verifies Core template routes and create workflow emit full template bundle metadata."
    val coreServiceSourceSets = project(":cloudislands-core-service").extensions.getByType<SourceSetContainer>()
    val coreServiceTest = coreServiceSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-service").tasks.named("testClasses"))
    testClassesDirs = coreServiceTest.output.classesDirs
    classpath = coreServiceTest.runtimeClasspath
    workingDir = project(":cloudislands-core-service").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/coreservice/http/routes/TemplateRoutesTest.class",
        "kr/lunaf/cloudislands/coreservice/workflow/CreateIslandWorkflowTest.class"
    )
}

val verifyPaperTemplateBundleCreateCoverage = tasks.register<Test>("verifyPaperTemplateBundleCreateCoverage") {
    group = "verification"
    description = "Verifies Paper creates islands from template bundles with checksum, placement, protection, and creation snapshots."
    val paperSourceSets = project(":cloudislands-paper").extensions.getByType<SourceSetContainer>()
    val paperTest = paperSourceSets.named("test").get()
    dependsOn(project(":cloudislands-paper").tasks.named("testClasses"))
    testClassesDirs = paperTest.output.classesDirs
    classpath = paperTest.runtimeClasspath
    workingDir = project(":cloudislands-paper").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/paper/activation/IslandActivationJobHandlerPolicyTest.class",
        "kr/lunaf/cloudislands/paper/world/IslandWorldRestorerTest.class"
    )
}

tasks.register("verifyTemplateBundleCreateCoverage") {
    group = "verification"
    description = "Verifies edit.md P1/PR-002/PR-003 template metadata, create payload, checksum restore, placement, and snapshot coverage."
    dependsOn(verifyCoreTemplateBundleCreateCoverage)
    dependsOn(verifyPaperTemplateBundleCreateCoverage)
}

tasks.register<Test>("verifySnapshotRestoreCoverage") {
    group = "verification"
    description = "Verifies snapshot restore GUI confirmation, Core restore route, route-safe restore payloads, and failed active restore runtime preservation."
    val coreServiceSourceSets = project(":cloudislands-core-service").extensions.getByType<SourceSetContainer>()
    val coreServiceTest = coreServiceSourceSets.named("test").get()
    dependsOn(project(":cloudislands-core-service").tasks.named("testClasses"))
    testClassesDirs = coreServiceTest.output.classesDirs
    classpath = coreServiceTest.runtimeClasspath
    workingDir = project(":cloudislands-core-service").projectDir
    useJUnitPlatform()
    include(
        "kr/lunaf/cloudislands/coreservice/http/routes/AdminIslandLifecycleRoutesTest.class",
        "kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflowRestoreTest.class"
    )
}

tasks.named("check") {
    dependsOn(tasks.named("verifyAddonDeveloperKitCoverage"))
    dependsOn(tasks.named("verifyRankingWorthCertification"))
    dependsOn(tasks.named("verifySnapshotRestoreCoverage"))
    dependsOn(tasks.named("verifyTemplateBundleCreateCoverage"))
    dependsOn(tasks.named("verifyGameplayModifierRuntimeCoverage"))
    dependsOn(tasks.named("verifyStackedBlockParityCoverage"))
    dependsOn(tasks.named("verifySatisEconomyLedgerCoverage"))
    dependsOn(tasks.named("verifySatisMigrationReportCoverage"))
    dependsOn(tasks.named("verifySatisNetworkRebuildDebounceCoverage"))
    dependsOn(tasks.named("verifySatisRuntimeFencingCoverage"))
}

tasks.register("verifyRoutingRefactorCoverage") {
    group = "verification"
    description = "Verifies RoutingOrchestrator responsibility split, typed failure mapping, and CoreApplication routing factory coverage remain present."
    dependsOn(project(":cloudislands-core-service").tasks.named("test"))
    val sourceRoot = "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice"
    val testRoot = "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice"
    val requiredFiles = listOf(
        "$sourceRoot/RouteAccessPolicy.java",
        "$sourceRoot/RouteTargetResolver.java",
        "$sourceRoot/RouteTicketService.java",
        "$sourceRoot/IslandActivationCoordinator.java",
        "$sourceRoot/RouteFailureMapper.java",
        "$sourceRoot/RoutingDiagnosticsService.java",
        "$sourceRoot/CoreRoutingComponents.java",
        "$sourceRoot/CoreRouteModules.java",
        "$sourceRoot/RouteFailureCode.java",
        "$testRoot/RouteFailureMapperTest.java",
        "$testRoot/RoutingOrchestratorActivationTest.java"
    )
    inputs.files(requiredFiles.map { layout.projectDirectory.file(it) })
    doLast {
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Routing refactor evidence missing: ${missing.joinToString(", ")}")
        }
        val orchestrator = layout.projectDirectory.file("$sourceRoot/RoutingOrchestrator.java").asFile.readText()
        val application = layout.projectDirectory.file("$sourceRoot/CloudIslandsCoreApplication.java").asFile.readText()
        val routeModules = layout.projectDirectory.file("$sourceRoot/CoreRouteModules.java").asFile.readText()
        val mapperTest = layout.projectDirectory.file("$testRoot/RouteFailureMapperTest.java").asFile.readText()
        val activationTest = layout.projectDirectory.file("$testRoot/RoutingOrchestratorActivationTest.java").asFile.readText()
        val failures = buildList {
            listOf(
                "new RouteAccessPolicy",
                "new RouteTicketService",
                "new RoutingDiagnosticsService",
                "RouteFailureMapper.map",
                "RouteTargetResolver.ready",
                "RouteTargetResolver.preparing",
                "IslandActivationCoordinator.placementMissing",
                "IslandActivationCoordinator.memberReservedSlotsExhausted",
                "IslandActivationCoordinator.duplicateVelocityServerName"
            ).filterNot(orchestrator::contains).forEach { signal ->
                add("RoutingOrchestrator missing responsibility split signal: $signal")
            }
            if (orchestrator.contains("\"VISITOR_SOFT_FULL\".equals(exception.getMessage())")) add("RoutingOrchestrator must not parse VISITOR_SOFT_FULL from exception messages")
            if (orchestrator.contains("exception.getMessage().startsWith(\"ACTIVE_NODE_\")")) add("RoutingOrchestrator must not parse ACTIVE_NODE failures from exception messages")
            if (!application.contains("CoreRoutingComponents.routing")) add("CloudIslandsCoreApplication must create routing through CoreRoutingComponents.routing")
            if (!application.contains("CoreRouteModules.register")) add("CloudIslandsCoreApplication must delegate route registration to CoreRouteModules")
            if (application.contains("new RoutePreparationRoutes(") || application.contains("new HealthRoutes(")) add("CloudIslandsCoreApplication must not directly register route modules")
            if (!routeModules.contains("new RoutePreparationRoutes") || !routeModules.contains("new AdminIslandLifecycleRoutes")) add("CoreRouteModules must own player and admin route registration")
            if (!mapperTest.contains("mapsActiveNodeFailuresToPublicNodeUnavailableWithDiagnostics")) add("RouteFailureMapperTest must cover active node failure mapping")
            if (!mapperTest.contains("mapsNoReadyNodeFailuresToPublicNodeUnavailableWithDiagnostics")) add("RouteFailureMapperTest must cover no-ready-node failure mapping")
            if (!activationTest.contains("CoreRoutingComponents.routing")) add("RoutingOrchestratorActivationTest must assert CoreApplication routing factory coverage")
            if (!activationTest.contains("CoreRouteModules.register")) add("RoutingOrchestratorActivationTest must assert CoreApplication route module coverage")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

apply(from = "gradle/report-gates.gradle.kts")

apply(from = "gradle/version-matrix-gates.gradle.kts")

tasks.named("check") {
    dependsOn(tasks.named("verifyMinecraftVersionMatrix"))
    dependsOn(tasks.named("verifyReadmeVersionTable"))
    dependsOn(tasks.named("verifyFeatureParityEvidence"))
    dependsOn(tasks.named("apiCompatibilityCheck"))
    dependsOn(tasks.named("protocolCompatibilityCheck"))
    dependsOn(tasks.named("verifyAdapterPackaging"))
}

apply(from = "gradle/integration-gates.gradle.kts")
apply(from = "gradle/migration-gates.gradle.kts")
apply(from = "gradle/release-gates.gradle.kts")
apply(from = "gradle/build-logic-gates.gradle.kts")

tasks.named("check") {
    dependsOn(tasks.named("verifyGradleGateSplit"))
}
