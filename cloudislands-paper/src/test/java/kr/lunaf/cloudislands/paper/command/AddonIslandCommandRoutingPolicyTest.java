package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AddonIslandCommandRoutingPolicyTest {
    @Test
    void routesExecutionCompletionHelpAndLifecycleCleanupThroughOneRegistry() throws Exception {
        String router = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java"));
        String completion = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String api = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"));
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));
        String registry = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/AddonIslandCommandRegistry.java"));

        assertTrue(router.contains("AddonIslandCommandRegistry.global().execute(player, label, effectiveArgs)"));
        assertTrue(router.contains("commands.addAll(AddonIslandCommandRegistry.global().helpCommands())"));
        assertTrue(completion.contains("AddonIslandCommandRegistry.global().tabComplete(player, alias, args)"));
        assertTrue(api.contains("AddonIslandCommandRegistry.global().unregisterAddon(safeId)"));
        assertTrue(api.contains("Disabled addon cannot register island commands"));
        assertTrue(plugin.contains("AddonIslandCommandRegistry.global().clear()"));
        assertTrue(registry.contains("PaperSchedulers.run(ticket.expectedPlugin()"));
        assertTrue(registry.contains("ticket.isCurrent(plugin, lifecycleGeneration.get(), activePlayer)"));
        assertTrue(registry.contains("never fall back to Bukkit calls on the completion thread"));
    }
}
