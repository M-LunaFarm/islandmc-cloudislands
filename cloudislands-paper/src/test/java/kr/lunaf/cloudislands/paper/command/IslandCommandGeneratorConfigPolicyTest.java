package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandCommandGeneratorConfigPolicyTest {
    @Test
    void islandGeneratorInfoUsesRuntimeDefaultGeneratorKey() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        String registrar = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/command/PaperCommandRegistrar.java"), StandardCharsets.UTF_8);
        String controller = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"), StandardCharsets.UTF_8);
        String backend = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"), StandardCharsets.UTF_8);
        String progression = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/command/IslandProgressionCommandHandler.java"), StandardCharsets.UTF_8);

        assertTrue(registrar.contains("plugin.runtimeConfig().generator().defaultKey()"));
        assertTrue(controller.contains("String defaultGeneratorKey"));
        assertTrue(backend.contains("String defaultGeneratorKey"));
        assertTrue(backend.contains("new IslandProgressionCommandHandler(plugin, coreApiClient, levelScanService, runtimeServices, defaultGeneratorKey)"));
        assertTrue(progression.contains("new GeneratorInfoUseCase(coreApiClient, ConfigGeneratorRules.load(plugin), defaultGeneratorKey)"));
    }
}
