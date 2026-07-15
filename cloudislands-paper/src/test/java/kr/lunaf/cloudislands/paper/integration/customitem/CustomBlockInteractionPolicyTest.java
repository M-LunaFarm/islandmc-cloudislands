package kr.lunaf.cloudislands.paper.integration.customitem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class CustomBlockInteractionPolicyTest {
    @Test
    void customBlockRightClickRequiresContainerPermission() {
        assertEquals(
            IslandPermission.OPEN_CONTAINER,
            CustomBlockInteractionPolicy.requiredPermission(Action.RIGHT_CLICK_BLOCK, true, IslandPermission.INTERACT)
        );
    }

    @Test
    void vanillaAndNonRightClickActionsPreserveGranularPermission() {
        assertEquals(
            IslandPermission.USE_REDSTONE,
            CustomBlockInteractionPolicy.requiredPermission(Action.RIGHT_CLICK_BLOCK, false, IslandPermission.USE_REDSTONE)
        );
        assertEquals(
            IslandPermission.BREAK,
            CustomBlockInteractionPolicy.requiredPermission(Action.LEFT_CLICK_BLOCK, true, IslandPermission.BREAK)
        );
        assertEquals(
            IslandPermission.INTERACT,
            CustomBlockInteractionPolicy.requiredPermission(Action.PHYSICAL, false, null)
        );
    }
}
