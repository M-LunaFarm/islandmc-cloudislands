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
    private static final String TITLE = "섬 메뉴";
    private final MessageRenderer messages;

    public IslandMainMenu() {
        this(null);
    }

    public IslandMainMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.GRASS_BLOCK, message(messages, "main-menu-home-name", "내 섬으로 이동"), message(messages, "main-menu-home-left", "좌클릭: /섬 홈"), message(messages, "main-menu-home-right", "우클릭: /섬 홈관리")));
        inventory.setItem(11, item(Material.OAK_SAPLING, message(messages, "main-menu-create-name", "섬 생성"), message(messages, "main-menu-create-command", "/섬 생성")));
        inventory.setItem(12, item(Material.ENDER_PEARL, message(messages, "main-menu-warp-name", "섬 워프"), message(messages, "main-menu-warp-command", "/섬 워프")));
        inventory.setItem(13, item(Material.COMPASS, message(messages, "main-menu-visit-name", "섬 방문"), message(messages, "main-menu-visit-left", "좌클릭: 공개 섬 목록"), message(messages, "main-menu-visit-right", "우클릭: /섬 랜덤방문")));
        inventory.setItem(14, item(Material.NAME_TAG, message(messages, "main-menu-member-name", "멤버 관리"), message(messages, "main-menu-member-command", "/섬 멤버")));
        inventory.setItem(15, item(Material.COMPARATOR, message(messages, "main-menu-settings-name", "섬 설정"), message(messages, "main-menu-settings-command", "/섬 설정")));
        inventory.setItem(16, item(Material.GOLD_BLOCK, message(messages, "main-menu-ranking-name", "섬 랭킹"), message(messages, "main-menu-ranking-command", "/섬 랭킹")));
        if (player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.node")) {
            inventory.setItem(17, item(Material.COMMAND_BLOCK, message(messages, "main-menu-admin-name", "관리자 메뉴"), message(messages, "main-menu-admin-command", "/ciadmin node menu")));
        }
        inventory.setItem(18, item(Material.FILLED_MAP, message(messages, "main-menu-my-islands-name", "내 섬 목록"), message(messages, "main-menu-my-islands-command", "/섬 목록")));
        inventory.setItem(19, item(Material.MAP, message(messages, "main-menu-info-name", "섬 정보"), message(messages, "main-menu-info-command", "/섬 정보")));
        inventory.setItem(20, item(Material.EMERALD, message(messages, "main-menu-bank-name", "섬 은행"), message(messages, "main-menu-bank-command", "/섬 은행")));
        inventory.setItem(21, item(Material.BOOK, message(messages, "main-menu-mission-name", "미션"), message(messages, "main-menu-mission-command", "/섬 미션")));
        inventory.setItem(22, item(Material.CHEST, message(messages, "main-menu-snapshot-name", "스냅샷"), message(messages, "main-menu-snapshot-command", "/섬 스냅샷")));
        inventory.setItem(23, item(Material.BEACON, message(messages, "main-menu-upgrade-name", "업그레이드"), message(messages, "main-menu-upgrade-command", "/섬 업그레이드")));
        inventory.setItem(24, item(Material.WRITABLE_BOOK, message(messages, "main-menu-chat-name", "섬 채팅"), message(messages, "main-menu-chat-command", "/섬 채팅")));
        inventory.setItem(25, item(Material.HOPPER, message(messages, "main-menu-limit-name", "제한"), message(messages, "main-menu-limit-command", "/섬 제한")));
        inventory.setItem(26, item(Material.GRASS_BLOCK, message(messages, "main-menu-biome-name", "바이옴"), message(messages, "main-menu-biome-command", "/섬 바이옴")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        player.closeInventory();
        if (slot == 10) {
            player.performCommand(event.isRightClick() ? "섬 홈관리" : "섬 홈");
        } else if (slot == 11) {
            player.performCommand("섬 생성메뉴");
        } else if (slot == 12) {
            player.performCommand("섬 워프");
        } else if (slot == 13) {
            if (event.isRightClick()) {
                player.performCommand("섬 랜덤방문");
            } else {
                player.performCommand("섬 방문");
            }
        } else if (slot == 14) {
            player.performCommand("섬 멤버관리");
        } else if (slot == 15) {
            player.performCommand("섬 설정");
        } else if (slot == 16) {
            player.performCommand("섬 랭킹");
        } else if (slot == 17) {
            player.performCommand("ciadmin node menu");
        } else if (slot == 18) {
            player.performCommand("섬 목록");
        } else if (slot == 19) {
            player.performCommand("섬 정보");
        } else if (slot == 20) {
            player.performCommand("섬 은행");
        } else if (slot == 21) {
            player.performCommand("섬 미션");
        } else if (slot == 22) {
            player.performCommand("섬 스냅샷");
        } else if (slot == 23) {
            player.performCommand("섬 업그레이드");
        } else if (slot == 24) {
            player.performCommand("섬 채팅");
        } else if (slot == 25) {
            player.performCommand("섬 제한");
        } else if (slot == 26) {
            player.performCommand("섬 바이옴");
        }
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
