package kr.lunaf.cloudislands.paper.integration.customitem;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomBlockProtectionPolicyTest {
    @Test
    void bootstrapInjectsSharedCustomIdentityIntoEarlyInteractionProtection() throws Exception {
        Path root = repositoryRoot();
        String bootstrap = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String listener = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(bootstrap.contains("plugin.stackAmounts,\n                plugin.customBlockKeys,\n                plugin.messages"));
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)\n    public void onInteract(PlayerInteractEvent event)"));
        assertTrue(listener.contains("customBlockKeys.isCustomBlock(event.getClickedBlock())"));
        assertTrue(listener.contains("CustomBlockInteractionPolicy.requiredPermission("));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("cloudislands-paper"))) {
            return current;
        }
        Path parent = current.getParent();
        return parent != null && Files.exists(parent.resolve("cloudislands-paper")) ? parent : current;
    }
}
