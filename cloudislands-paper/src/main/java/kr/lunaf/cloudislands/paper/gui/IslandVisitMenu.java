package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandVisitMenu implements Listener {
    private static final String TITLE_KEY = "visit-menu-title";
    private static final String TITLE = "섬 방문";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/visit.yml",
        new GuiMenuDefinition("island.visit", 6, TITLE_KEY, Map.of(
            "open", "island.visit.open",
            "public", "island.visit.public.open",
            "random", "island.visit.random",
            "target", "island.visit.target",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandVisitMenu() {
        this(null);
    }

    public IslandVisitMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandVisitMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.publicIslands(client, 45)
            .thenAccept(islands -> openSync(plugin, player, session, islands, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "visit-menu-load-failed", "공개 섬 목록을 불러오지 못했습니다."), "island.visit.open", "island.main.open");
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<PublicIslandView> islands, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()));
            if (islands.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                List<Integer> islandSlots = GuiMenuRenderer.slots(MENU, "_");
                for (int index = 0; index < islands.size() && index < islandSlots.size(); index++) {
                    PublicIslandView island = islands.get(index);
                    inventory.setItem(islandSlots.get(index), GuiItems.action(GuiMenuRenderer.material(MENU, "_", "GRASS_BLOCK"), island.name(), "island.visit.target",
                        Map.of("target", island.islandId()),
                        message(messages, "visit-menu-owner", "소유자: ") + shortId(island.ownerUuid()),
                        message(messages, "visit-menu-level", "레벨: ") + island.level(),
                        message(messages, "visit-menu-worth", "가치: ") + island.worth(),
                        message(messages, "visit-menu-click-to-visit", "클릭하면 방문합니다.")));
                }
            }
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "알 수 없음";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

}
