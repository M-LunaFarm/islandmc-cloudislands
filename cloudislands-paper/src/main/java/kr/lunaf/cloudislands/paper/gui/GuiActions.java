package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class GuiActions {
    private GuiActions() {
    }

    public static Optional<GuiAction> fromItem(ItemStack item) {
        return from(GuiItems.actionId(item), GuiItems.data(item));
    }

    public static Optional<GuiAction> from(String actionId) {
        return from(actionId, Map.of());
    }

    public static Optional<GuiAction> from(String actionId, Map<String, String> data) {
        return GuiActionParser.parse(actionId, data);
    }
}
