package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandVisitorStatsMenu implements Listener {
    private static final String TITLE = "방문 통계";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/visitor-stats.yml",
        new GuiMenuDefinition("island.visitor-stats", 6, "menu.visitor-stats.title", Map.of(
            "open", "island.visitor-stats.open",
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandVisitorStatsMenu() {
        this(null);
    }

    public IslandVisitorStatsMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandVisitorStatsMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        new IslandNavigationUseCase(client).visitorStats(islandId, 27)
            .thenAccept(stats -> openSync(plugin, player, session, stats, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "visitor-stats-menu-load-failed", "방문 통계를 불러오지 못했습니다."), "island.visitor-stats.open", "island.settings.open");
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, IslandVisitorStatsView stats, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"_".equals(item.symbol()));
            IslandVisitorStatsView safeStats = stats == null ? new IslandVisitorStatsView("", 0L, 0L, List.of()) : stats;
            setItem(inventory, "T", messages, message(messages, "visitor-stats-menu-total", "전체 방문: ") + safeStats.totalVisits());
            setItem(inventory, "U", messages, message(messages, "visitor-stats-menu-unique", "고유 방문자: ") + safeStats.uniqueVisitors());
            setRecentVisitors(inventory, safeStats.recentVisitors(), messages);
            player.openInventory(inventory);
        });
    }

    private static void setRecentVisitors(Inventory inventory, List<IslandVisitorStatsView.RecentVisitorView> visitors, MessageRenderer messages) {
        List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
        List<IslandVisitorStatsView.RecentVisitorView> entries = visitors == null ? List.of() : visitors;
        for (int index = 0; index < entries.size() && index < slots.size(); index++) {
            IslandVisitorStatsView.RecentVisitorView visitor = entries.get(index);
            int slot = slots.get(index);
            MENU.item("_").ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, Map.of(), visitorLore(visitor, messages))));
        }
        if (entries.isEmpty() && !slots.isEmpty()) {
            MENU.item("_").ifPresent(item -> inventory.setItem(slots.getFirst(), GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of(message(messages, "visitor-stats-menu-empty", "아직 방문 기록이 없습니다.")))));
        }
    }

    private static void setItem(Inventory inventory, String symbol, MessageRenderer messages, String... lore) {
        GuiMenuRenderer.slots(MENU, symbol).forEach(slot -> MENU.itemAt(slot)
            .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, item.data(), List.of(lore)))));
    }

    private static List<String> visitorLore(IslandVisitorStatsView.RecentVisitorView visitor, MessageRenderer messages) {
        return List.of(
            message(messages, "visitor-stats-menu-visitor", "방문자: ") + shortId(visitor.visitorUuid()),
            message(messages, "visitor-stats-menu-last-visited", "마지막 방문: ") + fallback(visitor.lastVisitedAt(), message(messages, "visitor-stats-menu-unknown-time", "알 수 없음"))
        );
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
