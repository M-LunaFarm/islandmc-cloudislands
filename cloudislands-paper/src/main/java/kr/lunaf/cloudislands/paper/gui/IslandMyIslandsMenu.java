package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PlayerIslandView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandMyIslandsMenu implements Listener {
    private static final String TITLE_KEY = "my-islands-menu-title";
    private static final String TITLE = "내 섬 목록";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/my-islands.yml",
        new GuiMenuDefinition("island.my-islands", 6, TITLE_KEY, Map.of(
            "open", "island.list.open",
            "visit", "island.visit.target",
            "create", "island.create.open",
            "public", "island.visit.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandMyIslandsMenu() {
        this(null);
    }

    public IslandMyIslandsMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandMyIslandsMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.playerIslands(client, player.getUniqueId())
            .thenAccept(islands -> openSync(plugin, player, session, islands, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "my-islands-menu-load-failed", "내 섬 목록을 불러오지 못했습니다."), "island.list.open", "island.main.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<PlayerIslandView> islands, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()));
            if (islands.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                for (int index = 0; index < islands.size() && index < 45; index++) {
                    PlayerIslandView island = islands.get(index);
                    inventory.setItem(index, GuiItems.action(GuiMenuRenderer.material(MENU, island.role(), "_", "GRASS_BLOCK"), island.name(), "island.visit.target",
                        Map.of("target", island.islandId()),
                        message(messages, "my-islands-menu-role", "역할: ") + island.role(),
                        message(messages, "my-islands-menu-state", "상태: ") + island.state(),
                        message(messages, "my-islands-menu-level", "레벨: ") + island.level(),
                        message(messages, "my-islands-menu-worth", "가치: ") + island.worth(),
                        message(messages, "my-islands-menu-click-to-visit", "클릭하면 이 섬으로 이동합니다.")));
                }
            }
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

}
