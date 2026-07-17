package kr.lunaf.cloudislands.paper.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView.NodeView;
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

public final class AdminStorageMenu implements Listener {
    private static final String TITLE_KEY = "admin-storage-menu-title";
    private static final String TITLE = "섬 스토리지 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-storage.yml",
        new GuiMenuDefinition("admin.storage", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("status", "admin.storage.status"),
            Map.entry("page", "admin.storage.page"),
            Map.entry("refresh", "admin.storage.page"),
            Map.entry("verify", "admin.storage.verify.prompt"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminStorageMenu() {
        this(null);
    }

    public AdminStorageMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminStorageMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-storage-menu-loading", "스토리지 상태를 불러오는 중입니다."));
        client.adminStorage().status()
            .thenAccept(status -> openSync(plugin, player, session, status, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-storage-menu-load-failed", "스토리지 상태를 불러오지 못했습니다."),
                    "admin.storage.page",
                    pageData(requestedPage),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, AdminStorageStatusView status, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<NodeView> entries = storageEntries(status);
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1)
                + " (" + unavailableCount(entries) + "/" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), nodeItem(entries.get(offset + index), messages));
            }
            GuiMenuRenderer.setSymbolItem(inventory, MENU, "H", messages, pageData(page), summaryLore(entries, messages));
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
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    static List<NodeView> storageEntries(AdminStorageStatusView status) {
        if (status == null || status.nodes() == null) {
            return List.of();
        }
        return status.nodes().stream()
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparing(NodeView::storageAvailable)
                .thenComparing(NodeView::primaryDegraded, Comparator.reverseOrder())
                .thenComparing(NodeView::saveRetryQueueTotal, Comparator.reverseOrder())
                .thenComparing(NodeView::nodeId))
            .toList();
    }

    static Map<String, String> pageData(int page) {
        return Map.of("page", Integer.toString(Math.max(0, page)));
    }

    static String nodeTitle(NodeView node) {
        String state = !node.storageAvailable() ? "DOWN" : node.primaryDegraded() ? "DEGRADED" : "OK";
        return safeLine(node.nodeId(), 30) + " / " + state;
    }

    static List<String> nodeLore(NodeView node, MessageRenderer messages) {
        return List.of(
            message(messages, "admin-storage-menu-backend-prefix", "백엔드: ") + safeLine(node.backend(), 32),
            message(messages, "admin-storage-menu-available-prefix", "사용 가능: ") + yesNo(node.storageAvailable()),
            message(messages, "admin-storage-menu-primary-prefix", "Primary degraded: ") + yesNo(node.primaryDegraded()),
            message(messages, "admin-storage-menu-retry-prefix", "저장 재시도 큐: ") + node.saveRetryQueueTotal(),
            message(messages, "admin-storage-menu-upload-prefix", "업로드: ") + seconds(node.uploadSeconds()),
            message(messages, "admin-storage-menu-download-prefix", "다운로드: ") + seconds(node.downloadSeconds()),
            message(messages, "admin-storage-menu-health-failures-prefix", "상태 확인 실패: ") + node.healthCheckFailures(),
            message(messages, "admin-storage-menu-transfer-failures-prefix", "전송 실패: ") + (node.uploadFailures() + node.downloadFailures()),
            message(messages, "admin-storage-menu-operation-failures-prefix", "작업 실패: ") + node.operationFailures(),
            message(messages, "admin-storage-menu-total-failures-prefix", "전체 실패: ") + node.totalFailures()
        );
    }

    static List<String> summaryLore(List<NodeView> nodes, MessageRenderer messages) {
        List<NodeView> entries = nodes == null ? List.of() : nodes.stream().filter(Objects::nonNull).toList();
        long degraded = entries.stream().filter(NodeView::primaryDegraded).count();
        long retries = entries.stream().mapToLong(NodeView::saveRetryQueueTotal).sum();
        long failures = entries.stream().mapToLong(NodeView::totalFailures).sum();
        return List.of(
            message(messages, "admin-storage-menu-summary-total-prefix", "전체 노드: ") + entries.size(),
            message(messages, "admin-storage-menu-summary-unavailable-prefix", "사용 불가: ") + unavailableCount(entries),
            message(messages, "admin-storage-menu-summary-degraded-prefix", "Primary degraded: ") + degraded,
            message(messages, "admin-storage-menu-summary-retries-prefix", "저장 재시도: ") + retries,
            message(messages, "admin-storage-menu-summary-failures-prefix", "누적 실패: ") + failures,
            message(messages, "admin-storage-menu-refresh-action", "클릭: 상태 새로고침")
        );
    }

    private static ItemStack nodeItem(NodeView node, MessageRenderer messages) {
        Material material = !node.storageAvailable()
            ? Material.RED_DYE
            : node.primaryDegraded() || node.saveRetryQueueTotal() > 0L ? Material.YELLOW_DYE : Material.LIME_DYE;
        return GuiItems.action(material, nodeTitle(node), "", nodeLore(node, messages).toArray(String[]::new));
    }

    private static long unavailableCount(List<NodeView> nodes) {
        return nodes.stream().filter(node -> !node.storageAvailable()).count();
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages, pageData(page), List.of());
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%.3f s", Math.max(0.0D, value));
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
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
