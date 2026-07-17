package kr.lunaf.cloudislands.paper.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminRouteMenu implements Listener {
    private static final String TITLE_KEY = "admin-route-menu-title";
    private static final String TITLE = "섬 라우트 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-route.yml",
        new GuiMenuDefinition("admin.route", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.route.clear.prepare"),
            Map.entry("page", "admin.route.page"),
            Map.entry("refresh", "admin.route.page"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminRouteMenu() {
        this(null);
    }

    public AdminRouteMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminRouteMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-route-menu-loading", "라우트 티켓을 불러오는 중입니다."));
        client.adminRoutes().debug(new UUID(0L, 0L))
            .thenAccept(debug -> openSync(plugin, player, session, debug, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-route-menu-load-failed", "라우트 티켓을 불러오지 못했습니다."),
                    "admin.route.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, AdminRouteDebugView debug, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<RouteEntry> entries = routeEntries(debug);
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            int sessions = debug == null ? 0 : debug.sessions().size();
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1)
                + " (" + entries.size() + "/" + sessions + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), routeItem(entries.get(offset + index), messages, page));
            }
            if (entries.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            if (page > 0) {
                setPageItem(inventory, "P", page - 1, messages);
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", page + 1, messages);
            }
            setPageItem(inventory, "R", page, messages);
            player.openInventory(inventory);
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.equals("gui.close")) {
            player.closeInventory();
            return;
        }
        GuiClick click = GuiClick.from(event);
        if (actionId.equals("admin.route.clear.prepare") && click != GuiClick.SHIFT_RIGHT) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static List<RouteEntry> routeEntries(AdminRouteDebugView debug) {
        if (debug == null) {
            return List.of();
        }
        LinkedHashMap<String, RouteEntry> entries = new LinkedHashMap<>();
        for (AdminRouteTicketView ticket : debug.tickets()) {
            RouteEntry entry = new RouteEntry(
                ticket.ticketId(), ticket.playerUuid(), ticket.islandId(), ticket.action(), ticket.state(),
                ticket.targetNode(), ticket.targetWorld(), ticket.targetServerName(), ticket.expiresAt()
            );
            entries.put(routeKey(entry), entry);
        }
        for (AdminRouteSessionView routeSession : debug.sessions()) {
            RouteEntry entry = new RouteEntry(
                routeSession.ticketId(), routeSession.playerUuid(), "", "SESSION", "ACTIVE",
                routeSession.targetNode(), "", routeSession.targetServerName(), routeSession.expiresAt()
            );
            entries.putIfAbsent(routeKey(entry), entry);
        }
        return List.copyOf(entries.values());
    }

    public static Material clearConfirmationMaterial() {
        return GuiMenuRenderer.material(MENU, "C", "REDSTONE_TORCH");
    }

    static ItemStack routeItem(RouteEntry entry, MessageRenderer messages, int page) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "COMPASS"),
            routeTitle(entry),
            "admin.route.clear.prepare",
            routeActionData(entry, page),
            routeLore(entry, messages).toArray(String[]::new)
        );
    }

    static Map<String, String> routeActionData(RouteEntry entry, int page) {
        return Map.of(
            "ticketId", entry.ticketId(),
            "playerUuid", entry.playerUuid(),
            "page", Integer.toString(Math.max(0, page))
        );
    }

    static String routeTitle(RouteEntry entry) {
        return safeLine(entry.action(), 24) + " / " + safeLine(entry.state(), 18) + " #" + shortId(entry.ticketId());
    }

    static List<String> routeLore(RouteEntry entry, MessageRenderer messages) {
        return List.of(
            message(messages, "admin-route-menu-player-prefix", "플레이어: ") + shortId(entry.playerUuid()),
            message(messages, "admin-route-menu-island-prefix", "섬: ") + shortId(entry.islandId()),
            message(messages, "admin-route-menu-node-prefix", "대상 노드: ") + safeLine(entry.targetNode(), 32),
            message(messages, "admin-route-menu-server-prefix", "대상 서버: ") + safeLine(entry.targetServerName(), 32),
            message(messages, "admin-route-menu-world-prefix", "대상 월드: ") + safeLine(entry.targetWorld(), 40),
            message(messages, "admin-route-menu-expires-prefix", "만료: ") + safeLine(entry.expiresAt(), 48),
            message(messages, "admin-route-menu-shift-right-action", "Shift+우클릭: 라우트 정리 확인")
        );
    }

    private static String routeKey(RouteEntry entry) {
        return entry.ticketId().isBlank() ? entry.playerUuid() : entry.ticketId();
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(Math.max(0, page))), List.of());
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String safeLine(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isBlank()) {
            return "-";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    record RouteEntry(
        String ticketId,
        String playerUuid,
        String islandId,
        String action,
        String state,
        String targetNode,
        String targetWorld,
        String targetServerName,
        String expiresAt
    ) {
        RouteEntry {
            ticketId = normalize(ticketId);
            playerUuid = normalize(playerUuid);
            islandId = normalize(islandId);
            action = normalize(action);
            state = normalize(state);
            targetNode = normalize(targetNode);
            targetWorld = normalize(targetWorld);
            targetServerName = normalize(targetServerName);
            expiresAt = normalize(expiresAt);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
