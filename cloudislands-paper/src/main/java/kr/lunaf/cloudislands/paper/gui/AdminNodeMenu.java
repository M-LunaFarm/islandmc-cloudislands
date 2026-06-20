package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.NodeSummaryView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminNodeMenu implements Listener {
    private static final String MENU_ID = "admin.node";
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
        open(player, nodeId, "", messages);
    }

    public static void open(Player player, String nodeId, String nodeInfoBody, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
        inventory.setItem(4, nodeSummaryItem(PaperGuiViews.nodeSummary(nodeId, nodeInfoBody), messages));
        inventory.setItem(10, adminActionItem(Material.COMPASS, message(messages, "admin-node-menu-list-name", "노드 목록"), "admin.node.list", nodeId, message(messages, "admin-node-menu-list-description", "신규 활성화 배정 가능 여부와 차단 사유를 함께 확인합니다.")));
        inventory.setItem(11, adminActionItem(Material.ENDER_EYE, message(messages, "admin-node-menu-info-name", "현재 노드 정보"), "admin.node.info", nodeId, message(messages, "admin-node-menu-info-description", "선택한 노드의 활성화 배정 상태를 확인합니다.")));
        inventory.setItem(12, adminActionItem(Material.GRASS_BLOCK, message(messages, "admin-node-menu-islands-name", "현재 노드 섬 현황"), "admin.node.islands", nodeId, message(messages, "admin-node-menu-islands-description", "활성 섬 UUID와 상태를 확인합니다."), message(messages, "admin-node-menu-islands-block-reason", "배정 차단 사유는 노드 정보에서 확인합니다.")));
        inventory.setItem(13, adminActionItem(Material.REDSTONE_TORCH, message(messages, "admin-node-menu-drain-name", "현재 노드 Drain"), "admin.node.drain", nodeId));
        inventory.setItem(14, adminActionItem(Material.LEVER, message(messages, "admin-node-menu-undrain-name", "현재 노드 Undrain"), "admin.node.undrain", nodeId));
        inventory.setItem(15, adminActionItem(Material.HOPPER, message(messages, "admin-node-menu-sweep-name", "장애 스윕"), "admin.node.sweep", nodeId));
        inventory.setItem(16, adminActionItem(Material.MAP, message(messages, "admin-node-menu-where-name", "활성 섬 조회"), "admin.island.where.prompt", nodeId, message(messages, "admin-node-menu-where-description", "섬 UUID로 현재 위치 노드를 확인합니다.")));
        inventory.setItem(17, adminActionItem(Material.MINECART, message(messages, "admin-node-menu-migrate-name", "부하 이동"), "admin.island.migrate.prompt", nodeId, message(messages, "admin-node-menu-migrate-description", "섬 UUID와 대상 노드를 입력해 이동합니다.")));
        inventory.setItem(18, adminActionItem(Material.IRON_DOOR, message(messages, "admin-node-menu-kickall-name", "현재 노드 플레이어 로비 이동"), "admin.node.kickall.prepare", nodeId, message(messages, "admin-node-menu-kickall-description", "이 노드의 접속자를 로비로 이동합니다."), message(messages, "admin-node-menu-danger-click", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(19, adminActionItem(Material.BELL, message(messages, "admin-node-menu-shutdown-name", "현재 노드 안전 종료"), "admin.node.shutdown-safe.prepare", nodeId, message(messages, "admin-node-menu-shutdown-description", "Drain 후 접속자를 로비로 이동합니다."), message(messages, "admin-node-menu-danger-click", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(22, item(Material.BOOK, message(messages, "admin-node-menu-help-name", "관리 명령 도움말"), message(messages, "admin-node-menu-help-status-command", "/ciadmin status"), message(messages, "admin-node-menu-help-node-list-command", "/ciadmin node list"), message(messages, "admin-node-menu-help-island-where-command", "/ciadmin island where <uuid>")));
        inventory.setItem(24, item(Material.CLOCK, message(messages, "admin-node-menu-status-name", "관리 상태"), message(messages, "admin-node-menu-status-command", "/ciadmin status")));
        inventory.setItem(26, item(Material.OAK_DOOR, message(messages, "admin-node-menu-close-name", "닫기"), message(messages, "admin-node-menu-close", "메뉴를 닫습니다.")));
        player.openInventory(inventory);
    }

    private static ItemStack nodeSummaryItem(NodeSummaryView summary, MessageRenderer messages) {
        return item(Material.NETHER_STAR,
            message(messages, "admin-node-menu-summary-name", "노드 요약: ") + summary.nodeId(),
            message(messages, "admin-node-menu-summary-state", "state: ") + fallback(summary.state(), "unknown"),
            message(messages, "admin-node-menu-summary-pool", "pool: ") + fallback(summary.pool(), "island"),
            message(messages, "admin-node-menu-summary-players", "players: ") + summary.players() + "/" + summary.softPlayerCap() + "/" + summary.hardPlayerCap(),
            message(messages, "admin-node-menu-summary-mspt", "mspt: ") + fallback(summary.mspt(), "0"),
            message(messages, "admin-node-menu-summary-active-islands", "active islands: ") + summary.activeIslands() + "/" + summary.maxActiveIslands(),
            message(messages, "admin-node-menu-summary-queue", "queue: ") + summary.activationQueue() + "/" + summary.maxActivationQueue(),
            message(messages, "admin-node-menu-summary-policy", "작업 순서: Drain -> View Islands -> Move Load -> Shutdown Safe"));
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
        if (slot >= event.getView().getTopInventory().getSize()) {
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
            CommandListPolicy.Page commandPage = CommandListPolicy.page(adminHelpCommands(messages), 1, "ciadmin command list");
            String title = message(messages, "admin-node-menu-help", "CloudIslands 관리자 명령어 목록");
            player.sendMessage(title.replace(CommandListPolicy.HEADER_SUFFIX, "").trim() + " " + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + CommandListPolicy.HEADER_SUFFIX);
            commandPage.entries().forEach(command -> player.sendMessage(CommandListPolicy.ENTRY_PREFIX + command));
            if (commandPage.previousCommand() != null && !commandPage.previousCommand().isBlank()) {
                player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
            }
            if (commandPage.nextCommand() != null && !commandPage.nextCommand().isBlank()) {
                player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
            }
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            GuiActionRegistry.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
        }
    }

    private static List<String> adminHelpCommands(MessageRenderer messages) {
        return List.of(
            commandName(message(messages, "admin-node-menu-help-status-command", "/ciadmin status")),
            commandName(message(messages, "admin-node-menu-help-node-list-command", "/ciadmin node list")),
            commandName(message(messages, "admin-node-menu-help-node-info-command", "/ciadmin node info [node]")),
            commandName(message(messages, "admin-node-menu-help-node-islands-command", "/ciadmin node islands [node] [limit]")),
            commandName(message(messages, "admin-node-menu-help-node-kickall-command", "/ciadmin node kickall [node]")),
            commandName(message(messages, "admin-node-menu-help-node-shutdown-command", "/ciadmin node shutdown-safe [node]")),
            commandName(message(messages, "admin-node-menu-help-island-where-command", "/ciadmin island where <uuid>"))
        );
    }

    private static String commandName(String command) {
        String value = command == null ? "" : command.trim();
        while (value.startsWith("/")) {
            value = value.substring(1).trim();
        }
        return value;
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

    private static ItemStack adminActionItem(Material material, String name, String actionId, String nodeId, String... lore) {
        return GuiItems.action(material, name, actionId, Map.of("nodeId", nodeId), lore);
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
