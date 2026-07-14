package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandMissionProgressListenerPolicyTest {
    @Test
    void gameplayProgressObservesFinalUncancelledOutcomes() throws IOException {
        String source = source();
        String monitorHandler = "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)";

        assertEquals(9, occurrences(source, monitorHandler), "every gameplay trigger must observe the final event outcome");
        assertFalse(source.contains("@EventHandler(ignoreCancelled = true)"), "default-priority handlers can credit actions cancelled later");
        assertFalse(source.contains("\n    @EventHandler\n"), "all mission handlers must declare the final-outcome policy explicitly");
    }

    @Test
    void craftingUsesCapacityBoundedShiftClickAmount() throws IOException {
        String source = source();

        assertTrue(source.contains("ItemStack result = event.getCurrentItem()"), "custom recipe output must be authoritative");
        assertTrue(source.contains("result = event.getRecipe().getResult()"), "vanilla recipe output must remain a safe fallback");
        assertTrue(source.contains("CraftingMissionAmount.from(event, result)"));
        assertTrue(source.contains("if (craftedAmount <= 0L)"));
        assertTrue(source.contains("MissionProgressTriggers.crafting(materialKey(result.getType()), craftedAmount)"));
    }

    @Test
    void asyncDefinitionAndProgressCallbacksUseCapturedPlayerIdentity() throws IOException {
        String source = source();
        int progressAt = source.indexOf("private void progressAt(");
        int matchingDefinitions = source.indexOf("private CompletableFuture<List<MissionProgressTriggers.Trigger>>", progressAt);
        String method = source.substring(progressAt, matchingDefinitions);

        assertTrue(method.contains("UUID actorUuid = player.getUniqueId();"));
        assertTrue(method.contains("progress(islandId, actorUuid, trigger)"));
        assertFalse(method.substring(method.indexOf("matchingDefinitionTriggers")).contains("player.getUniqueId()"),
            "HTTP completion callbacks must not access a live Bukkit Player off-thread");
    }

    private static String source() throws IOException {
        Path root = repositoryRoot();
        return Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListener.java"));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("cloudislands-paper"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("cloudislands-paper"))) {
            return parent;
        }
        return current;
    }
}
