package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDependencyPolicyTest {
    @Test
    void doesNotImportLegacySkyblockRuntimeApis() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> forbiddenImports = List.of(
                "import com.bgsoftware.superiorskyblock",
                "import world.bentobox.",
                "import com.wasteofplastic.askyblock",
                "import us.talabrek.ultimateskyblock",
                "import com.iridium.iridiumskyblock",
                "import com.massivecraft.factions"
        );

        try (var files = Files.walk(sourceRoot)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(source.contains(forbiddenImport), javaFile + " imports legacy runtime API " + forbiddenImport);
                }
            }
        }
    }

    @Test
    void pluginMetadataDoesNotDependOnLegacySkyblockPlugins() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        List<Path> descriptorFiles = List.of(
                Path.of("src/main/resources/plugin.yml"),
                repoRoot.resolve("cloudislands-paper/src/main/resources/plugin.yml"),
                repoRoot.resolve("cloudislands-satis/src/main/resources/cloudislands-addon.yml")
        );
        List<String> dependencyKeys = List.of(
                "depend:",
                "softdepend:",
                "loadbefore:",
                "required-plugins:",
                "plugin-dependencies:",
                "runtime-dependencies:"
        );
        List<String> forbiddenProviders = List.of(
                "superiorskyblock",
                "bentobox",
                "askyblock",
                "uskyblock",
                "iridiumskyblock"
        );

        for (Path descriptorFile : descriptorFiles) {
            List<String> dependencyLines = descriptorDependencyLines(Files.readString(descriptorFile), dependencyKeys);

            for (String line : dependencyLines) {
                String lower = line.toLowerCase();
                for (String forbiddenProvider : forbiddenProviders) {
                    assertFalse(
                            lower.contains(forbiddenProvider),
                            descriptorFile + " declares legacy skyblock runtime dependency " + forbiddenProvider + ": " + line
                    );
                }
            }
        }
    }

    @Test
    void descriptorDependencyParserReadsNestedDependencyBlocks() {
        String descriptor = """
                name: Example
                softdepend:
                  - SuperiorSkyblock2
                  - PlaceholderAPI
                commands:
                  island:
                    usage: /island
                required-plugins:
                  BentoBox: optional
                  CloudIslands: required
                """;

        List<String> dependencyLines = descriptorDependencyLines(
                descriptor,
                List.of("depend:", "softdepend:", "loadbefore:", "required-plugins:", "plugin-dependencies:", "runtime-dependencies:")
        );

        assertTrue(dependencyLines.contains("softdepend:"));
        assertTrue(dependencyLines.contains("- SuperiorSkyblock2"));
        assertTrue(dependencyLines.contains("- PlaceholderAPI"));
        assertTrue(dependencyLines.contains("required-plugins:"));
        assertTrue(dependencyLines.contains("BentoBox: optional"));
        assertTrue(dependencyLines.contains("CloudIslands: required"));
        assertFalse(dependencyLines.contains("commands:"));
        assertFalse(dependencyLines.contains("usage: /island"));
    }

    private List<String> descriptorDependencyLines(String descriptor, List<String> dependencyKeys) {
        List<String> lines = descriptor.lines().toList();
        java.util.ArrayList<String> dependencyLines = new java.util.ArrayList<>();
        boolean inDependencyBlock = false;
        int dependencyIndent = -1;
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = rawLine.length() - rawLine.stripLeading().length();
            boolean startsDependencyKey = dependencyKeys.stream().anyMatch(trimmed::startsWith);
            if (startsDependencyKey) {
                dependencyLines.add(trimmed);
                inDependencyBlock = !trimmed.contains("[") && !trimmed.contains("]") && trimmed.endsWith(":");
                dependencyIndent = indent;
                continue;
            }
            if (inDependencyBlock && indent > dependencyIndent && (trimmed.startsWith("-") || trimmed.contains(":"))) {
                dependencyLines.add(trimmed);
                continue;
            }
            inDependencyBlock = false;
            dependencyIndent = -1;
        }
        return List.copyOf(dependencyLines);
    }

    @Test
    void gradleBuildFilesDoNotDeclareLegacySkyblockDependencies() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        List<Path> buildFiles = List.of(
                repoRoot.resolve("build.gradle.kts"),
                repoRoot.resolve("cloudislands-satis/build.gradle.kts"),
                repoRoot.resolve("cloudislands-migration/build.gradle.kts"),
                repoRoot.resolve("cloudislands-core-service/build.gradle.kts"),
                repoRoot.resolve("cloudislands-paper/build.gradle.kts"),
                repoRoot.resolve("cloudislands-velocity/build.gradle.kts")
        );
        List<String> dependencyConfigurations = List.of(
                "api(",
                "implementation(",
                "compileOnly(",
                "runtimeOnly(",
                "annotationProcessor(",
                "testImplementation(",
                "testRuntimeOnly("
        );
        List<String> forbiddenProviders = List.of(
                "superiorskyblock",
                "bgsoftware",
                "bentobox",
                "askyblock",
                "uskyblock",
                "ultimateskyblock",
                "iridiumskyblock"
        );

        for (Path buildFile : buildFiles) {
            List<String> dependencyLines = Files.readString(buildFile).lines()
                    .map(String::trim)
                    .filter(line -> dependencyConfigurations.stream().anyMatch(line::startsWith))
                    .toList();

            for (String dependencyLine : dependencyLines) {
                String lower = dependencyLine.toLowerCase();
                for (String forbiddenProvider : forbiddenProviders) {
                    assertFalse(
                            lower.contains(forbiddenProvider),
                            buildFile + " declares legacy skyblock dependency " + forbiddenProvider + ": " + dependencyLine
                    );
                }
            }
        }
    }

    @Test
    void jarManifestsExposeCompleteAddonStateBulkEndpoints() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        String satisBuild = Files.readString(repoRoot.resolve("cloudislands-satis/build.gradle.kts"));
        String coreBuild = Files.readString(repoRoot.resolve("cloudislands-core-service/build.gradle.kts"));
        String clientBuild = Files.readString(repoRoot.resolve("cloudislands-core-client/build.gradle.kts"));
        List<String> globalEndpoints = List.of(
                "/v1/addons/state/table/bulk",
                "/v1/addons/state/table-key-value/bulk-save",
                "/v1/addons/state/table/key-value/bulk-save",
                "/v1/addons/state/table/key-value/bulk/save",
                "/v1/addons/state/table/key-value/bulk",
                "/v1/addons/state/table/key-value/bulk-load",
                "/v1/addons/state/table/load",
                "/v1/addons/state/table/bulk-set"
        );
        List<String> islandEndpoints = List.of(
                "/v1/addons/islands/state/table/bulk",
                "/v1/addons/islands/state/table-key-value/bulk-save",
                "/v1/addons/islands/state/table/key-value/bulk-save",
                "/v1/addons/islands/state/table/key-value/bulk/save",
                "/v1/addons/islands/state/table/key-value/bulk",
                "/v1/addons/islands/state/table/key-value/bulk-load",
                "/v1/addons/islands/state/table/load",
                "/v1/addons/islands/state/table/bulk-set"
        );

        assertTrue(satisBuild.contains("CloudIslands-Satis-Core-API-Bulk-Endpoints"));
        assertTrue(coreBuild.contains("CloudIslands-Core-Addon-State-Bulk-Endpoints"));
        assertTrue(coreBuild.contains("CloudIslands-Core-Addon-Island-State-Bulk-Endpoints"));
        assertTrue(clientBuild.contains("CloudIslands-Core-Client-Bulk-State-Endpoints"));
        for (String endpoint : globalEndpoints) {
            assertTrue(satisBuild.contains(endpoint), "Satis manifest missing global endpoint " + endpoint);
            assertTrue(coreBuild.contains(endpoint), "Core manifest missing global endpoint " + endpoint);
            assertTrue(clientBuild.contains(endpoint), "Core client manifest missing global endpoint " + endpoint);
        }
        for (String endpoint : islandEndpoints) {
            assertTrue(satisBuild.contains(endpoint), "Satis manifest missing island endpoint " + endpoint);
            assertTrue(coreBuild.contains(endpoint), "Core manifest missing island endpoint " + endpoint);
            assertTrue(clientBuild.contains(endpoint), "Core client manifest missing island endpoint " + endpoint);
        }
    }

    @Test
    void cloudIslandsApiStaysProvidedForSatisRuntime() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        String satisBuild = Files.readString(repoRoot.resolve("cloudislands-satis/build.gradle.kts"));

        assertTrue(satisBuild.contains("compileOnly(project(\":cloudislands-api\"))"));
        assertTrue(satisBuild.contains("testImplementation(project(\":cloudislands-api\"))"));
        assertTrue(satisBuild.contains("id(\"com.gradleup.shadow\")") || satisBuild.contains("alias(libs.plugins.shadow)"));
        assertTrue(satisBuild.contains("tasks.shadowJar"));
        assertTrue(satisBuild.contains("mergeServiceFiles()"));
        assertFalse(satisBuild.contains("implementation(project(\":cloudislands-api\"))"));
        assertFalse(satisBuild.contains("project(\":cloudislands-core-service\")"));
        assertFalse(satisBuild.contains("project(\":cloudislands-paper\")"));
        assertFalse(satisBuild.contains("project(\":cloudislands-velocity\")"));
        assertFalse(satisBuild.contains("project(\":cloudislands-storage\")"));
        assertFalse(satisBuild.contains("kr.lunaf.cloudislands.coreservice"));
    }

    @Test
    void satisSourcesDoNotImportCloudIslandsInternals() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        Path satisSourceRoot = repoRoot.resolve("cloudislands-satis/src/main/java");
        List<String> forbiddenImports = List.of(
                "kr.lunaf.cloudislands.coreservice",
                "kr.lunaf.cloudislands.paper",
                "kr.lunaf.cloudislands.velocity",
                "kr.lunaf.cloudislands.storage"
        );

        try (Stream<Path> sourceFiles = Files.walk(satisSourceRoot)) {
            for (Path sourceFile : sourceFiles.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(sourceFile);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(
                            source.contains("import " + forbiddenImport),
                            sourceFile + " imports CloudIslands internal implementation package " + forbiddenImport
                    );
                }
            }
        }
    }

    @Test
    void addonDescriptorShipsAsSeparateSidecarArtifact() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        String distributionBuild = Files.readString(repoRoot.resolve("gradle/distribution.gradle.kts"));

        assertTrue(distributionBuild.contains("tasks.register<Copy>(\"distAddonDescriptors\")"));
        assertTrue(distributionBuild.contains("Collects optional CloudIslands addon descriptors separately from addon jars."));
        assertTrue(distributionBuild.contains("src/main/resources/cloudislands-addon.yml"));
        assertTrue(distributionBuild.contains("rename { \"$projectName.yml\" }"));
        assertTrue(distributionBuild.contains("dist/addon-descriptors"));
        assertTrue(distributionBuild.contains("into(\"addon-descriptors\")"));
        assertTrue(distributionBuild.contains("dependsOn(tasks.named(\"distAddonDescriptors\"))"));
    }

    @Test
    void rootBuildCheckExcludesMarkdownDocumentsFromArtifacts() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        String rootBuild = Files.readString(repoRoot.resolve("build.gradle.kts"));
        String distributionBuild = Files.readString(repoRoot.resolve("gradle/distribution.gradle.kts"));

        assertTrue(rootBuild.contains("tasks.register(\"verifyMarkdownDocsExcludedFromArtifacts\")"));
        assertTrue(rootBuild.contains("markdown documents are allowed in source but excluded from packaged artifacts"));
        assertTrue(rootBuild.contains("tasks.named(\"build\") {\n    dependsOn(tasks.named(\"verifyMarkdownDocsExcludedFromArtifacts\"))\n}"));
        assertTrue(rootBuild.contains("tasks.named(\"check\") {\n    dependsOn(tasks.named(\"verifyMarkdownDocsExcludedFromArtifacts\"))\n}"));
        assertTrue(distributionBuild.contains("dependsOn(tasks.named(\"verifyMarkdownDocsExcludedFromArtifacts\"))"));
    }
}
