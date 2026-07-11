package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.SnapshotView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandSnapshotMenu implements Listener {
    private static final String TITLE_KEY = "snapshot-menu-title";
    private static final String TITLE = "섬 스냅샷";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/snapshots.yml",
        new GuiMenuDefinition("island.snapshots", 6, TITLE_KEY, Map.of(
            "open", "island.snapshots.open",
            "list", "island.snapshots.list",
            "page", "island.snapshots.page",
            "create", "island.snapshot.create",
            "restore-prepare", "island.snapshot.restore.prepare",
            "restore-confirm", ConfirmationTokenPolicy.SNAPSHOT_RESTORE_CONFIRM_ACTION,
            "back", "island.danger.open",
            "settings", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandSnapshotMenu() {
        this(null);
    }

    public IslandSnapshotMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandSnapshotMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static org.bukkit.Material restoreConfirmationMaterial() {
        return GuiMenuRenderer.material(MENU, "C", "CHEST");
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, SnapshotRetentionPolicy.defaultPolicy());
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, SnapshotRetentionPolicy retentionPolicy) {
        open(plugin, client, player, islandId, messages, retentionPolicy, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, SnapshotRetentionPolicy retentionPolicy, int page) {
        SnapshotRetentionPolicy effectivePolicy = (retentionPolicy == null ? SnapshotRetentionPolicy.defaultPolicy() : retentionPolicy).normalized();
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        int queryLimit = Math.max(20, Math.min(100, effectivePolicy.retainedSnapshotCount()));
        PaperGuiViews.islandSnapshots(client, islandId, queryLimit)
            .thenAccept(snapshots -> openSync(plugin, player, session, islandId, snapshots, messages, effectivePolicy, page))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "snapshot-menu-load-failed", "섬 스냅샷을 불러오지 못했습니다."), "island.snapshots.open", "island.settings.open");
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
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String snapshotNo = data.getOrDefault("snapshotNo", "");
        if (!snapshotNo.isBlank()) {
            player.closeInventory();
            if (event.isShiftClick() && event.isRightClick()) {
                actions.execute(player, GuiActions.from("island.snapshot.restore.prepare", data).orElse(null), GuiClick.from(event));
                return;
            }
            if (event.isRightClick()) {
                player.sendMessage(message(messages, "snapshot-restore-confirm-required", "스냅샷 복원은 Shift+우클릭해야 실행됩니다."));
                return;
            }
            player.sendMessage(message(messages, "snapshot-menu-detail-title", "스냅샷 상세"));
            player.sendMessage("- " + message(messages, "snapshot-menu-number", "번호: ") + snapshotNo);
            player.sendMessage("- " + message(messages, "snapshot-menu-reason", "사유: ") + fallback(data.get("reason"), message(messages, "snapshot-menu-none", "없음")));
            player.sendMessage("- " + message(messages, "snapshot-menu-size", "크기: ") + fallback(data.get("sizeBytes"), "0") + message(messages, "snapshot-menu-size-unit", " bytes"));
            player.sendMessage("- " + message(messages, "snapshot-menu-created-at", "생성 시각: ") + fallback(data.get("createdAt"), message(messages, "snapshot-menu-no-created-info", "생성 정보 없음")));
            player.sendMessage("- " + message(messages, "snapshot-menu-node", "node: ") + fallback(data.get("nodeId"), message(messages, "snapshot-menu-none", "없음")));
            player.sendMessage("- " + message(messages, "snapshot-menu-checksum", "checksum: ") + fallback(data.get("checksum"), message(messages, "snapshot-menu-none", "없음")));
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
        }
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, UUID islandId, List<SnapshotView> snapshots, MessageRenderer messages, SnapshotRetentionPolicy retentionPolicy, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> snapshotSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, snapshotSlots.size());
            int maxPage = Math.max(0, (snapshots.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("E", "_", "P", "N").contains(item.symbol()));
            setRetentionItem(inventory, messages, retentionPolicy);
            if (snapshots.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                int offset = page * pageSize;
                for (int index = 0; index < pageSize && offset + index < snapshots.size(); index++) {
                    inventory.setItem(snapshotSlots.get(index), snapshotItem(snapshots.get(offset + index), messages));
                }
                if (page > 0) {
                    setPageItem(inventory, "P", islandId, page - 1, messages);
                }
                if (page < maxPage) {
                    setPageItem(inventory, "N", islandId, page + 1, messages);
                }
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack snapshotItem(SnapshotView snapshot, MessageRenderer messages) {
        return GuiItems.action(GuiMenuRenderer.material(MENU, "_", "PAPER"), message(messages, "snapshot-menu-title-prefix", "스냅샷 #") + snapshot.snapshotNo(), "island.snapshot.restore.prepare",
            Map.of(
                "snapshotNo", String.valueOf(snapshot.snapshotNo()),
                "reason", snapshot.reason(),
                "sizeBytes", String.valueOf(snapshot.sizeBytes()),
                "createdAt", snapshot.createdAt(),
                "nodeId", snapshot.nodeId(),
                "checksum", snapshot.checksum()
            ),
            message(messages, "snapshot-menu-reason", "사유: ") + (snapshot.reason().isBlank() ? message(messages, "snapshot-menu-none", "없음") : snapshot.reason()),
            message(messages, "snapshot-menu-size", "크기: ") + snapshot.sizeBytes() + message(messages, "snapshot-menu-size-unit", " bytes"),
            snapshot.createdAt().isBlank() ? message(messages, "snapshot-menu-no-created-info", "생성 정보 없음") : message(messages, "snapshot-menu-created-at", "생성 시각: ") + snapshot.createdAt(),
            snapshot.nodeId().isBlank() ? message(messages, "snapshot-menu-node-missing", "node: 없음") : message(messages, "snapshot-menu-node", "node: ") + snapshot.nodeId(),
            snapshot.checksum().isBlank() ? message(messages, "snapshot-menu-checksum-missing", "checksum: 없음") : message(messages, "snapshot-menu-checksum", "checksum: ") + shortChecksum(snapshot.checksum()),
            message(messages, "snapshot-menu-left-click", "좌클릭: 상세 보기"),
            message(messages, "snapshot-menu-shift-right-click", "Shift+우클릭: 이 스냅샷 복원 요청"));
    }

    private static void setRetentionItem(Inventory inventory, MessageRenderer messages, SnapshotRetentionPolicy retentionPolicy) {
        SnapshotRetentionPolicy policy = (retentionPolicy == null ? SnapshotRetentionPolicy.defaultPolicy() : retentionPolicy).normalized();
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "S", messages, Map.of(), List.of(
            message(messages, "snapshot-menu-retention-summary", "보존 정책: ") + "hourly=" + policy.keepHourly() + ", daily=" + policy.keepDaily() + ", weekly=" + policy.keepWeekly() + ", manual=" + policy.keepManual(),
            message(messages, "snapshot-menu-retention-total", "최대 보존 수: ") + policy.retainedSnapshotCount(),
            message(messages, "snapshot-menu-retention-checksum", "checksum: ") + policy.checksumAlgorithm(),
            message(messages, "snapshot-menu-retention-compress", "압축: ") + policy.compress()
        ));
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
    }

    private static void setPageItem(Inventory inventory, String symbol, UUID islandId, int page, MessageRenderer messages) {
        String key = symbol.equals("P") ? "snapshot-menu-previous-page" : "snapshot-menu-next-page";
        String fallback = symbol.equals("P") ? "이전 페이지" : "다음 페이지";
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("islandId", islandId.toString(), "page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        return checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
    }

}
