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
import org.bukkit.plugin.Plugin;

public final class IslandMyIslandsMenu implements Listener {
    private static final String TITLE_KEY = "my-islands-menu-title";
    private static final String TITLE = "내 섬 목록";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/my-islands.yml",
        new GuiMenuDefinition("island.my-islands", 6, TITLE_KEY, Map.of(
            "open", "island.list.open",
            "page", "island.list.page",
            "visit", "island.visit.target",
            "select", "island.select.target",
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
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int page) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.playerIslands(client, player.getUniqueId())
            .thenAccept(islands -> openSync(plugin, player, session, islands, messages, page))
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
        GuiClick click = GuiClick.from(event);
        String resolvedActionId = actionId.equals("island.visit.target") && click.right() ? "island.select.target" : actionId;
        player.closeInventory();
        actions.execute(player, GuiActions.from(resolvedActionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<PlayerIslandView> islands, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> islandSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, islandSlots.size());
            int maxPage = Math.max(0, (islands.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, MENU.titleKey(), TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("E", "P", "N").contains(item.symbol()));
            if (islands.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                int offset = page * pageSize;
                for (int index = 0; index < pageSize && offset + index < islands.size(); index++) {
                    PlayerIslandView island = islands.get(offset + index);
                    int slot = islandSlots.get(index);
                    MENU.item(island.role()).or(() -> MENU.item("_")).ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, island.name(),
                        Map.of("target", island.islandId()),
                        List.of(
                            message(messages, "my-islands-menu-role", "역할: ") + island.role(),
                            message(messages, "my-islands-menu-state", "상태: ") + island.state(),
                            message(messages, "my-islands-menu-level", "레벨: ") + island.level(),
                            message(messages, "my-islands-menu-worth", "가치: ") + island.worth(),
                            island.primary()
                                ? message(messages, "my-islands-menu-primary-selected", "현재 기본 섬")
                                : message(messages, "my-islands-menu-primary-not-selected", "기본 섬 아님"),
                            message(messages, "my-islands-menu-click-to-visit", "좌클릭: 이 섬으로 이동"),
                            message(messages, "my-islands-menu-right-click-to-select", "우클릭: 기본 섬으로 선택")
                        ))));
                }
                if (page > 0) {
                    setPageItem(inventory, "P", page - 1, messages, "my-islands-menu-previous-page", "이전 페이지");
                }
                if (page < maxPage) {
                    setPageItem(inventory, "N", page + 1, messages, "my-islands-menu-next-page", "다음 페이지");
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

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages, String key, String fallback) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages, Map.of("page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }

}
