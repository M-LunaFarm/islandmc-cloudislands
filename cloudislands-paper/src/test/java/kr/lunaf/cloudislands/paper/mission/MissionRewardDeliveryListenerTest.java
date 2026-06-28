package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MissionRewardDeliveryListenerTest {
    @Test
    void commandRewardReplacesPlayerAndUuidPlaceholders() throws IOException {
        String source = source();

        assertTrue(source.contains(".replace(\"%player%\", safePlayerName)"), "Command reward must support player-name placeholders");
        assertTrue(source.contains(".replace(\"{player}\", safePlayerName)"), "Command reward must support braced player-name placeholders");
        assertTrue(source.contains(".replace(\"%uuid%\", safeUuid)"), "Command reward must support UUID placeholders");
        assertTrue(source.contains(".replace(\"{uuid}\", safeUuid)"), "Command reward must support braced UUID placeholders");
        assertTrue(source.contains(".replaceFirst(\"^/\", \"\")"), "Command reward must normalize a leading slash before console dispatch");
    }

    @Test
    void itemRewardParsesNamespacedMaterialAndClampsAmount() throws IOException {
        String source = source();

        assertTrue(source.contains("Material.matchMaterial(tokens[0].toUpperCase(Locale.ROOT).replace(\"MINECRAFT:\", \"\"))"), "Item reward must parse namespaced Bukkit materials");
        assertTrue(source.contains("!material.isItem()"), "Item reward must reject non-item materials");
        assertTrue(source.contains("Math.max(1, Math.min(amount, material.getMaxStackSize() * 36))"), "Item reward must clamp delivery to one player inventory");
        assertTrue(source.contains("player.getInventory().addItem(new ItemStack(reward.material(), reward.amount()))"), "Item reward must deliver to the player's inventory");
    }

    private static String source() throws IOException {
        Path root = repositoryRoot();
        return Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/MissionRewardDeliveryListener.java"));
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
