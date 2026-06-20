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

public final class IslandMainMenu implements Listener {
    private static final String TITLE_KEY = "main-menu-title";
    private static final String TITLE = "섬 메뉴";
    private static final String MENU_ID = "island.main";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandMainMenu() {
        this(null);
    }

    public IslandMainMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandMainMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this(messages, new GuiActionRegistry(actions));
    }

    public IslandMainMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
        inventory.setItem(10, GuiItems.action(Material.GRASS_BLOCK, message(messages, "main-menu-home-name", "내 섬으로 이동"), "island.home", message(messages, "main-menu-home-left", "좌클릭: 홈 이동"), message(messages, "main-menu-home-right", "우클릭: 홈 관리")));
        inventory.setItem(11, GuiItems.action(Material.OAK_SAPLING, message(messages, "main-menu-create-name", "섬 생성"), "island.create.open", message(messages, "main-menu-create-command", "섬 생성 화면")));
        inventory.setItem(12, GuiItems.action(Material.ENDER_PEARL, message(messages, "main-menu-warp-name", "섬 워프"), "island.warps.open", message(messages, "main-menu-warp-command", "섬 워프 화면")));
        inventory.setItem(13, GuiItems.action(Material.COMPASS, message(messages, "main-menu-visit-name", "섬 방문"), "island.visit.open", message(messages, "main-menu-visit-left", "좌클릭: 공개 섬 목록"), message(messages, "main-menu-visit-right", "우클릭: 랜덤 방문")));
        inventory.setItem(14, GuiItems.action(Material.NAME_TAG, message(messages, "main-menu-member-name", "멤버 관리"), "island.members.open", message(messages, "main-menu-member-command", "멤버 관리")));
        inventory.setItem(15, GuiItems.action(Material.COMPARATOR, message(messages, "main-menu-settings-name", "섬 설정"), "island.settings.open", message(messages, "main-menu-settings-command", "섬 설정")));
        inventory.setItem(16, GuiItems.action(Material.GOLD_BLOCK, message(messages, "main-menu-ranking-name", "섬 랭킹"), "island.ranking.open", message(messages, "main-menu-ranking-command", "섬 랭킹")));
        if (player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.node")) {
            inventory.setItem(17, GuiItems.action(Material.COMMAND_BLOCK, message(messages, "main-menu-admin-name", "관리자 메뉴"), "admin.node.open", message(messages, "main-menu-admin-command", "관리자 노드 메뉴")));
        }
        inventory.setItem(18, GuiItems.action(Material.FILLED_MAP, message(messages, "main-menu-my-islands-name", "내 섬 목록"), "island.list.open", message(messages, "main-menu-my-islands-command", "내 섬 목록")));
        inventory.setItem(19, GuiItems.action(Material.MAP, message(messages, "main-menu-info-name", "섬 정보"), "island.info.open", message(messages, "main-menu-info-command", "섬 정보")));
        inventory.setItem(20, GuiItems.action(Material.EMERALD, message(messages, "main-menu-bank-name", "섬 은행"), "island.bank.open", message(messages, "main-menu-bank-command", "섬 은행")));
        inventory.setItem(21, GuiItems.action(Material.BOOK, message(messages, "main-menu-mission-name", "미션"), "island.missions.open", message(messages, "main-menu-mission-command", "섬 미션")));
        inventory.setItem(22, GuiItems.action(Material.CHEST, message(messages, "main-menu-snapshot-name", "스냅샷"), "island.snapshots.open", message(messages, "main-menu-snapshot-command", "스냅샷")));
        inventory.setItem(23, GuiItems.action(Material.BEACON, message(messages, "main-menu-upgrade-name", "업그레이드"), "island.upgrades.open", message(messages, "main-menu-upgrade-command", "업그레이드")));
        inventory.setItem(24, GuiItems.action(Material.WRITABLE_BOOK, message(messages, "main-menu-chat-name", "섬 채팅"), "island.chat.open", message(messages, "main-menu-chat-command", "섬 채팅")));
        inventory.setItem(25, GuiItems.action(Material.HOPPER, message(messages, "main-menu-limit-name", "제한"), "island.limits.open", message(messages, "main-menu-limit-command", "제한")));
        inventory.setItem(26, GuiItems.action(Material.GRASS_BLOCK, message(messages, "main-menu-biome-name", "바이옴"), "island.biome.open", message(messages, "main-menu-biome-command", "바이옴")));
        player.openInventory(inventory);
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
        GuiClick click = GuiClick.from(event);
        if (!click.supported()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        if (actionId.equals("island.visit.open") && click.right()) {
            actions.execute(player, "island.visit.random", GuiItems.data(event.getCurrentItem()), click);
            return;
        }
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
}
