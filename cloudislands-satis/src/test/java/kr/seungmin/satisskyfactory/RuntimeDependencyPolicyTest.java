package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeDependencyPolicyTest {
    @Test
    void doesNotImportLegacySkyblockRuntimeApis() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> forbiddenImports = List.of(
                "import com.bgsoftware.superiorskyblock",
                "import world.bentobox.",
                "import com.wasteofplastic.askyblock",
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
        }
    }
}
