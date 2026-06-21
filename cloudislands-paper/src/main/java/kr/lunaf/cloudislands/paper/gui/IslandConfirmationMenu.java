package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class IslandConfirmationMenu implements Listener {
    private static final String TITLE = "작업 확인";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/confirmation.yml",
        new GuiMenuDefinition("island.confirmation", 3, "confirmation-menu-title", Map.of(
            "cancel", "gui.close",
            "confirm", "gui.close"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandConfirmationMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this(messages, new GuiActionRegistry(actions));
    }

    public IslandConfirmationMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player, MessageRenderer messages, Confirmation confirmation) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE, item -> true);
        inventory.setItem(4, item(GuiMenuRenderer.material(MENU.itemAt(4).map(GuiMenuDefinition.MenuItem::materialKey).orElse("PAPER")), confirmation.title(), confirmation.description()));
        MENU.itemAt(11).ifPresent(item -> inventory.setItem(11, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of(), confirmation.cancelAction())));
        inventory.setItem(15, GuiItems.action(confirmation.material(), confirmation.confirmName(), confirmation.confirmAction(), confirmation.data(), confirmation.confirmLore()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        GuiClick click = GuiClick.from(event);
        if (click != GuiClick.LEFT) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    public record Confirmation(
        String title,
        String description,
        Material material,
        String confirmName,
        String confirmAction,
        Map<String, String> data,
        String confirmLore,
        String cancelAction
    ) {
        public static Confirmation of(String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
            return new Confirmation(title, description, material, confirmName, confirmAction, data == null ? Map.of() : Map.copyOf(data), confirmLore, cancelAction);
        }
    }
}
