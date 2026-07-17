package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminNodeListMenu implements Listener {
    private static final String TITLE_KEY = "admin-node-list-menu-title";
    private static final String TITLE = "섬 노드 목록";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-node-list.yml",
        new GuiMenuDefinition("admin.nodes", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.node.open"),
            Map.entry("page", "admin.node.page"),
            Map.entry("refresh", "admin.node.page"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminNodeListMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-node-list-menu-loading", "노드 목록을 불러오는 중입니다."));
        client.adminNodes().nodes()
            .thenAccept(nodes -> openSync(plugin, player, session, nodes, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-node-list-menu-load-failed", "노드 목록을 불러오지 못했습니다."),
                    "admin.node.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<IslandNodeSnapshot> nodes, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<IslandNodeSnapshot> entries = nodes == null ? List.of() : List.copyOf(nodes);
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1) + " (" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), nodeItem(entries.get(offset + index), messages));
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
        if (actionId.equals("admin.node.open") && click != GuiClick.LEFT) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static ItemStack nodeItem(IslandNodeSnapshot node, MessageRenderer messages) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "NETHER_STAR"),
            nodeTitle(node),
            "admin.node.open",
            nodeActionData(node),
            nodeLore(node, messages).toArray(String[]::new)
        );
    }

    static Map<String, String> nodeActionData(IslandNodeSnapshot node) {
        return Map.of("nodeId", node.nodeId());
    }

    static String nodeTitle(IslandNodeSnapshot node) {
        return safeLine(node.nodeId(), 30) + " / " + (node.state() == null ? "UNKNOWN" : node.state().name());
    }

    static List<String> nodeLore(IslandNodeSnapshot node, MessageRenderer messages) {
        String allocationBlockReason = node.allocationBlockReason() == null ? "" : node.allocationBlockReason().trim();
        return List.of(
            message(messages, "admin-node-list-menu-server-prefix", "서버: ") + safeLine(node.serverName(), 32),
            message(messages, "admin-node-list-menu-pool-prefix", "풀: ") + safeLine(node.pool(), 24),
            message(messages, "admin-node-list-menu-version-prefix", "버전: ") + safeLine(node.nodeVersion(), 32),
            message(messages, "admin-node-list-menu-players-prefix", "플레이어: ") + node.players() + "/" + node.softPlayerCap() + "/" + node.hardPlayerCap(),
            message(messages, "admin-node-list-menu-islands-prefix", "활성 섬: ") + node.activeIslands() + "/" + node.maxActiveIslands(),
            message(messages, "admin-node-list-menu-mspt-prefix", "MSPT: ") + String.format(Locale.ROOT, "%.2f", node.mspt()),
            message(messages, "admin-node-list-menu-queue-prefix", "활성화 큐: ") + node.activationQueue() + "/" + node.maxActivationQueue(),
            message(messages, "admin-node-list-menu-heap-prefix", "힙: ") + node.heapUsedMb() + "/" + node.heapMaxMb() + " MB",
            message(messages, "admin-node-list-menu-storage-prefix", "스토리지: ") + (node.storageAvailable() ? "OK" : "DOWN"),
            message(messages, "admin-node-list-menu-eligible-prefix", "신규 배정: ") + (node.eligibleForNewActivation() ? "YES" : "NO")
                + (allocationBlockReason.isBlank() ? "" : " / " + safeLine(allocationBlockReason, 48)),
            message(messages, "admin-node-list-menu-heartbeat-prefix", "마지막 하트비트: ") + (node.lastHeartbeat() == null ? "-" : node.lastHeartbeat().toString()),
            message(messages, "admin-node-list-menu-left-action", "좌클릭: 노드 상세 관리")
        );
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(Math.max(0, page))), List.of());
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
}
