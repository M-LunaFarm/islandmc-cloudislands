package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
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
    private final MessageRenderer messages;

    public IslandSnapshotMenu() {
        this(null);
    }

    public IslandSnapshotMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandSnapshots(islandId, 20)
            .thenAccept(body -> openSync(plugin, player, snapshots(body), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "snapshot-menu-load-failed", "섬 스냅샷을 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!message(messages, TITLE_KEY, TITLE).equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 45) {
            GuiActionRegistry.execute(player, "island.snapshot.create", java.util.Map.of("reason", "manual"), GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.snapshots.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String snapshotNo = loreValue(meta, "번호=");
        if (!snapshotNo.isBlank()) {
            if (event.isShiftClick() && event.isRightClick()) {
                GuiActionRegistry.execute(player, "island.snapshot.restore", java.util.Map.of("snapshotNo", String.valueOf(snapshotNo)), GuiClick.from(event));
                return;
            }
            if (event.isRightClick()) {
                player.sendMessage(message(messages, "snapshot-restore-confirm-required", "스냅샷 복원은 Shift+우클릭해야 실행됩니다."));
                return;
            }
            player.sendMessage(message(messages, "snapshot-menu-detail-title", "스냅샷 상세"));
            if (meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    player.sendMessage("- " + line);
                }
            }
        }
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static void openSync(Plugin plugin, Player player, List<Snapshot> snapshots, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(45, item(Material.CHEST, message(messages, "snapshot-menu-create-name", "새 스냅샷 생성"), message(messages, "snapshot-menu-create-command", "/섬 스냅샷생성 manual")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "snapshot-menu-refresh-name", "새로고침"), message(messages, "snapshot-menu-refresh-command", "/섬 스냅샷")));
            int slot = 0;
            if (snapshots.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "snapshot-menu-empty-title", "스냅샷 없음"), message(messages, "snapshot-menu-empty", "아직 생성된 섬 스냅샷이 없습니다.")));
            } else {
                for (Snapshot snapshot : snapshots.stream().limit(45).toList()) {
                    inventory.setItem(slot++, snapshotItem(snapshot, messages));
                }
            }
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "snapshot-menu-settings-name", "설정"), message(messages, "snapshot-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack snapshotItem(Snapshot snapshot, MessageRenderer messages) {
        return item(Material.PAPER, message(messages, "snapshot-menu-title-prefix", "스냅샷 #") + snapshot.snapshotNo(), "번호=" + snapshot.snapshotNo(), message(messages, "snapshot-menu-reason", "사유: ") + (snapshot.reason().isBlank() ? message(messages, "snapshot-menu-none", "없음") : snapshot.reason()), message(messages, "snapshot-menu-size", "크기: ") + snapshot.sizeBytes() + message(messages, "snapshot-menu-size-unit", " bytes"), snapshot.createdAt().isBlank() ? message(messages, "snapshot-menu-no-created-info", "생성 정보 없음") : message(messages, "snapshot-menu-created-at", "생성 시각: ") + snapshot.createdAt(), message(messages, "snapshot-menu-left-click", "좌클릭: 상세 보기"), message(messages, "snapshot-menu-shift-right-click", "Shift+우클릭: 이 스냅샷 복원 요청"));
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

    private static List<Snapshot> snapshots(String body) {
        List<Snapshot> snapshots = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            long snapshotNo = number(object, "snapshotNo");
            if (snapshotNo > 0L) {
                snapshots.add(new Snapshot(snapshotNo, text(object, "reason"), number(object, "sizeBytes"), text(object, "createdAt")));
            }
            index = objectEnd + 1;
        }
        return snapshots;
    }

    private static String loreValue(ItemMeta meta, String prefix) {
        if (meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long number(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private record Snapshot(long snapshotNo, String reason, long sizeBytes, String createdAt) {
    }
}
