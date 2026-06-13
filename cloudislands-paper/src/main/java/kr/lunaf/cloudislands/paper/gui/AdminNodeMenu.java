package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
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

public final class AdminNodeMenu implements Listener {
    private static final String TITLE_KEY = "admin-node-menu-title";
    private static final String TITLE = "섬 노드 관리";
    private final MessageRenderer messages;

    public AdminNodeMenu() {
        this(null);
    }

    public AdminNodeMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Player player, String nodeId) {
        open(player, nodeId, null);
    }

    public static void open(Player player, String nodeId, MessageRenderer messages) {
        Inventory inventory = Bukkit.createInventory(null, 27, message(messages, TITLE_KEY, TITLE));
        inventory.setItem(10, item(Material.COMPASS, message(messages, "admin-node-menu-list-name", "노드 목록"), message(messages, "admin-node-menu-list-command", "/ciadmin node list"), message(messages, "admin-node-menu-list-description", "신규 활성화 배정 가능 여부와 차단 사유를 함께 확인합니다.")));
        inventory.setItem(11, item(Material.ENDER_EYE, message(messages, "admin-node-menu-info-name", "현재 노드 정보"), message(messages, "admin-node-menu-info-command", "/ciadmin node info ") + nodeId, message(messages, "admin-node-menu-info-description", "선택한 노드의 활성화 배정 상태를 확인합니다.")));
        inventory.setItem(12, item(Material.GRASS_BLOCK, message(messages, "admin-node-menu-islands-name", "현재 노드 섬 현황"), message(messages, "admin-node-menu-islands-command", "/ciadmin node islands ") + nodeId + " 50", message(messages, "admin-node-menu-islands-description", "활성 섬 UUID와 상태를 확인합니다."), message(messages, "admin-node-menu-islands-block-reason", "배정 차단 사유는 노드 정보에서 확인합니다.")));
        inventory.setItem(13, item(Material.REDSTONE_TORCH, message(messages, "admin-node-menu-drain-name", "현재 노드 Drain"), message(messages, "admin-node-menu-drain-command", "/ciadmin node drain ") + nodeId));
        inventory.setItem(14, item(Material.LEVER, message(messages, "admin-node-menu-undrain-name", "현재 노드 Undrain"), message(messages, "admin-node-menu-undrain-command", "/ciadmin node undrain ") + nodeId));
        inventory.setItem(15, item(Material.HOPPER, message(messages, "admin-node-menu-sweep-name", "장애 스윕"), message(messages, "admin-node-menu-sweep-command", "/ciadmin node sweep ") + nodeId));
        inventory.setItem(16, item(Material.MAP, message(messages, "admin-node-menu-where-name", "활성 섬 조회"), message(messages, "admin-node-menu-where-command", "/ciadmin island where <uuid>"), message(messages, "admin-node-menu-where-description", "섬 UUID로 현재 위치 노드를 확인합니다.")));
        inventory.setItem(17, item(Material.MINECART, message(messages, "admin-node-menu-migrate-name", "부하 이동"), message(messages, "admin-node-menu-migrate-command", "/ciadmin island migrate <uuid> <node>"), message(messages, "admin-node-menu-migrate-description", "섬 UUID와 대상 노드를 입력해 이동합니다.")));
        inventory.setItem(18, item(Material.IRON_DOOR, message(messages, "admin-node-menu-kickall-name", "현재 노드 플레이어 로비 이동"), message(messages, "admin-node-menu-kickall-command", "/ciadmin node kickall ") + nodeId, message(messages, "admin-node-menu-kickall-description", "이 노드의 접속자를 로비로 이동합니다."), message(messages, "admin-node-menu-danger-click", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(19, item(Material.BELL, message(messages, "admin-node-menu-shutdown-name", "현재 노드 안전 종료"), message(messages, "admin-node-menu-shutdown-command", "/ciadmin node shutdown-safe ") + nodeId, message(messages, "admin-node-menu-shutdown-description", "Drain 후 접속자를 로비로 이동합니다."), message(messages, "admin-node-menu-danger-click", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(22, item(Material.BOOK, message(messages, "admin-node-menu-help-name", "관리 명령 도움말"), message(messages, "admin-node-menu-help-status-command", "/ciadmin status"), message(messages, "admin-node-menu-help-node-list-command", "/ciadmin node list"), message(messages, "admin-node-menu-help-island-where-command", "/ciadmin island where <uuid>")));
        inventory.setItem(24, item(Material.CLOCK, message(messages, "admin-node-menu-status-name", "관리 상태"), message(messages, "admin-node-menu-status-command", "/ciadmin status")));
        inventory.setItem(26, item(Material.OAK_DOOR, message(messages, "admin-node-menu-close-name", "닫기"), message(messages, "admin-node-menu-close", "메뉴를 닫습니다.")));
        player.openInventory(inventory);
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
        if (slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        player.closeInventory();
        if (slot == 26) {
            return;
        }
        if ((slot == 18 || slot == 19) && (!event.isShiftClick() || !event.isRightClick())) {
            player.sendMessage(message(messages, "admin-node-menu-danger-required", "위험한 노드 작업은 Shift+우클릭해야 실행됩니다."));
            return;
        }
        if (slot == 22) {
            player.sendMessage(message(messages, "admin-node-menu-help", "사용법: /ciadmin node list, /ciadmin node info [node], /ciadmin node islands [node] [limit], /ciadmin node kickall [node], /ciadmin node shutdown-safe [node], /ciadmin island where <uuid>"));
            return;
        }
        String command = firstCommand(meta);
        if (command != null && command.contains("<")) {
            player.sendMessage(message(messages, "admin-node-menu-direct-required", "직접 입력이 필요한 명령입니다: ") + command);
            return;
        }
        if (command != null) {
            player.performCommand(command.substring(1));
        }
    }

    private static String firstCommand(ItemMeta meta) {
        if (!meta.hasLore() || meta.getLore() == null) {
            return null;
        }
        return meta.getLore().stream()
            .filter(line -> line.startsWith("/"))
            .findFirst()
            .orElse(null);
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

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }
}
