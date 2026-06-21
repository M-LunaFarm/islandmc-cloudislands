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
    private static final String TITLE_KEY = "admin-node-menu-title";
    private static final String TITLE = "섬 노드 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-node.yml",
        new GuiMenuDefinition("admin.node", 3, TITLE_KEY, Map.ofEntries(
            Map.entry("open", "admin.node.open"),
            Map.entry("list", "admin.node.list"),
            Map.entry("info", "admin.node.info"),
            Map.entry("islands", "admin.node.islands"),
            Map.entry("drain", "admin.node.drain"),
            Map.entry("undrain", "admin.node.undrain"),
            Map.entry("sweep", "admin.node.sweep"),
            Map.entry("where", "admin.island.where.prompt"),
            Map.entry("migrate", "admin.island.migrate.prompt"),
            Map.entry("kickall", "admin.node.kickall.prepare"),
            Map.entry("shutdown-safe", "admin.node.shutdown-safe.prepare"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public AdminNodeMenu() {
        this(null);
    }

    public AdminNodeMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminNodeMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player, String nodeId) {
        open(player, nodeId, null);
    }

    public static void open(Player player, String nodeId, MessageRenderer messages) {
        open(player, nodeId, "", messages);
    }

    public static void open(Player player, String nodeId, String nodeInfoBody, MessageRenderer messages) {
        open(player, nodeId, PaperGuiViews.nodeSummary(nodeId, nodeInfoBody), messages);
    }

    public static void open(Player player, String nodeId, NodeSummaryView summary, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE, item -> true);
        inventory.setItem(4, nodeSummaryItem(summary == null ? PaperGuiViews.nodeSummary(nodeId, "") : summary, messages));
        for (int slot = 10; slot <= 19; slot++) {
            final int currentSlot = slot;
            MENU.itemAt(slot).ifPresent(item -> inventory.setItem(currentSlot, GuiMenuRenderer.item(MENU, item, messages, Map.of("nodeId", nodeId))));
        }
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
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String actionId = GuiItems.actionId(event.getCurrentItem());
        player.closeInventory();
        if (actionId.equals("gui.close")) {
            return;
        }
        if (data.getOrDefault("requireShiftRight", "false").equals("true") && (!event.isShiftClick() || !event.isRightClick())) {
            player.sendMessage(message(messages, "admin-node-menu-danger-required", "위험한 노드 작업은 Shift+우클릭해야 실행됩니다."));
            return;
        }
        if (data.getOrDefault("mode", "").equals("help")) {
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
        if (!actionId.isBlank()) {
            actions.execute(player, actionId, data, GuiClick.from(event));
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

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
