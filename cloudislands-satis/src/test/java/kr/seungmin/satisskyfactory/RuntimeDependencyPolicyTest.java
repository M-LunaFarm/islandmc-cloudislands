package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        List<String> dependencyLines = plugin.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("depend:")
                        || line.startsWith("softdepend:")
                        || line.startsWith("loadbefore:"))
                .toList();

        for (String line : dependencyLines) {
            String lower = line.toLowerCase();
            assertFalse(lower.contains("superiorskyblock"), "plugin.yml declares SuperiorSkyblock runtime dependency: " + line);
            assertFalse(lower.contains("bentobox"), "plugin.yml declares BentoBox runtime dependency: " + line);
            assertFalse(lower.contains("askyblock"), "plugin.yml declares ASkyBlock runtime dependency: " + line);
            assertFalse(lower.contains("uskyblock"), "plugin.yml declares uSkyBlock runtime dependency: " + line);
            assertFalse(lower.contains("iridiumskyblock"), "plugin.yml declares IridiumSkyblock runtime dependency: " + line);
        }
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
        assertTrue(satisBuild.contains("val jarDependencyProjects = embeddedProjects"));
        assertFalse(satisBuild.contains("val jarDependencyProjects = embeddedProjects + listOf(\":cloudislands-api\")"));
        assertFalse(satisBuild.contains("implementation(project(\":cloudislands-api\"))"));
    }

    @Test
    void addonDescriptorShipsAsSeparateSidecarArtifact() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
        String rootBuild = Files.readString(repoRoot.resolve("build.gradle.kts"));

        assertTrue(rootBuild.contains("tasks.register<Copy>(\"distAddonDescriptors\")"));
        assertTrue(rootBuild.contains("Collects optional CloudIslands addon descriptors separately from addon jars."));
        assertTrue(rootBuild.contains("src/main/resources/cloudislands-addon.yml"));
        assertTrue(rootBuild.contains("rename { \"$projectName.yml\" }"));
        assertTrue(rootBuild.contains("dist/addon-descriptors"));
        assertTrue(rootBuild.contains("into(\"addon-descriptors\")"));
        assertTrue(rootBuild.contains("dependsOn(tasks.named(\"distAddonDescriptors\"))"));
    }
}
