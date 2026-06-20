package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.SnapshotView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandSnapshotMenu implements Listener {
    private static final String TITLE_KEY = "snapshot-menu-title";
    private static final String TITLE = "섬 스냅샷";
    private static final String MENU_ID = "island.snapshots";
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

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandSnapshots(client, islandId, 20)
            .thenAccept(snapshots -> openSync(plugin, player, session, snapshots, messages))
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 45) {
            actions.execute(player, "island.snapshot.create", java.util.Map.of("reason", "manual"), GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            actions.execute(player, "island.snapshots.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            actions.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String snapshotNo = data.getOrDefault("snapshotNo", "");
        if (!snapshotNo.isBlank()) {
            if (event.isShiftClick() && event.isRightClick()) {
                actions.execute(player, "island.snapshot.restore.prepare", java.util.Map.of("snapshotNo", String.valueOf(snapshotNo)), GuiClick.from(event));
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
        }
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<SnapshotView> snapshots, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, session, 54, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(45, item(Material.CHEST, message(messages, "snapshot-menu-create-name", "새 스냅샷 생성"), message(messages, "snapshot-menu-create-command", "/섬 스냅샷생성 manual")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "snapshot-menu-refresh-name", "새로고침"), message(messages, "snapshot-menu-refresh-command", "/섬 스냅샷")));
            int slot = 0;
            if (snapshots.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "snapshot-menu-empty-title", "스냅샷 없음"), message(messages, "snapshot-menu-empty", "아직 생성된 섬 스냅샷이 없습니다.")));
            } else {
                for (SnapshotView snapshot : snapshots.stream().limit(45).toList()) {
                    inventory.setItem(slot++, snapshotItem(snapshot, messages));
                }
            }
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "snapshot-menu-settings-name", "설정"), message(messages, "snapshot-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack snapshotItem(SnapshotView snapshot, MessageRenderer messages) {
        return GuiItems.action(Material.PAPER, message(messages, "snapshot-menu-title-prefix", "스냅샷 #") + snapshot.snapshotNo(), "island.snapshot.restore.prepare",
            Map.of(
                "snapshotNo", String.valueOf(snapshot.snapshotNo()),
                "reason", snapshot.reason(),
                "sizeBytes", String.valueOf(snapshot.sizeBytes()),
                "createdAt", snapshot.createdAt()
            ),
            message(messages, "snapshot-menu-reason", "사유: ") + (snapshot.reason().isBlank() ? message(messages, "snapshot-menu-none", "없음") : snapshot.reason()),
            message(messages, "snapshot-menu-size", "크기: ") + snapshot.sizeBytes() + message(messages, "snapshot-menu-size-unit", " bytes"),
            snapshot.createdAt().isBlank() ? message(messages, "snapshot-menu-no-created-info", "생성 정보 없음") : message(messages, "snapshot-menu-created-at", "생성 시각: ") + snapshot.createdAt(),
            message(messages, "snapshot-menu-left-click", "좌클릭: 상세 보기"),
            message(messages, "snapshot-menu-shift-right-click", "Shift+우클릭: 이 스냅샷 복원 요청"));
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

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
