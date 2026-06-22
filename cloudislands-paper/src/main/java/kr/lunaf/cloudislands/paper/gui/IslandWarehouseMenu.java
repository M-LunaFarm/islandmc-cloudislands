package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase.WarehouseItemView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandWarehouseMenu implements Listener {
    private static final String TITLE = "공동 창고";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/warehouse.yml",
        new GuiMenuDefinition("island.warehouse", 6, "menu.warehouse.title", Map.of(
            "open", "island.warehouse.open",
            "list", "island.warehouse.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandWarehouseMenu() {
        this(null);
    }

    public IslandWarehouseMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandWarehouseMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        new IslandWarehouseUseCase(client).listItems(islandId, 36)
            .thenAccept(items -> openSync(plugin, player, session, items, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "warehouse-menu-load-failed", "섬 창고를 불러오지 못했습니다."), "island.warehouse.open", "island.settings.open");
                return null;
            });
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<WarehouseItemView> items, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"_".equals(item.symbol()));
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            List<WarehouseItemView> entries = items == null ? List.of() : items;
            for (int index = 0; index < entries.size() && index < slots.size(); index++) {
                WarehouseItemView itemView = entries.get(index);
                int slot = slots.get(index);
                MENU.item("_").ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, Map.of(), itemLore(itemView, messages))));
            }
            if (entries.isEmpty()) {
                MENU.item("_").ifPresent(item -> inventory.setItem(slots.getFirst(), GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of(message(messages, "warehouse-menu-empty", "섬 창고가 비어 있습니다.")))));
            }
            player.openInventory(inventory);
        });
    }

    private static List<String> itemLore(WarehouseItemView item, MessageRenderer messages) {
        return List.of(
            message(messages, "warehouse-menu-material", "재료: ") + item.materialKey(),
            message(messages, "warehouse-menu-amount", "수량: ") + item.amount()
        );
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
