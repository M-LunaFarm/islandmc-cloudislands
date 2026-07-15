package kr.lunaf.cloudislands.paper.integration.customitem;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.bukkit.event.block.Action;

/** Keeps custom machine interfaces behind the island container boundary. */
public final class CustomBlockInteractionPolicy {
    private CustomBlockInteractionPolicy() {
    }

    public static IslandPermission requiredPermission(Action action, boolean customBlock, IslandPermission fallback) {
        if (customBlock && action == Action.RIGHT_CLICK_BLOCK) {
            return IslandPermission.OPEN_CONTAINER;
        }
        return fallback == null ? IslandPermission.INTERACT : fallback;
    }
}
