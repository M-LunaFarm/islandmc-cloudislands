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

public final class IslandChatMenu implements Listener {
    private static final String TITLE = "섬 채팅";
    private final MessageRenderer messages;

    public IslandChatMenu() {
        this(null);
    }

    public IslandChatMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.WRITABLE_BOOK, "섬 채팅 보내기", message(messages, "chat-menu-island-usage", "사용법: /섬 채팅 <메시지>"), "섬 전체 채널로 기록됩니다."));
        inventory.setItem(12, item(Material.BOOK, "팀 채팅 보내기", message(messages, "chat-menu-team-usage", "사용법: /섬 팀채팅 <메시지>"), "섬 팀 채널로 기록됩니다."));
        inventory.setItem(14, item(Material.CLOCK, "최근 섬 로그", "/섬 로그", "채팅 기록도 섬 로그에서 확인합니다."));
        inventory.setItem(15, item(Material.COMPARATOR, "섬 설정", "/섬 설정"));
        inventory.setItem(16, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
        inventory.setItem(22, item(Material.OAK_DOOR, "닫기", "메뉴를 닫습니다."));
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
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName();
        player.closeInventory();
        if (name.equals("섬 채팅 보내기")) {
            player.sendMessage(message(messages, "chat-menu-island-usage", "사용법: /섬 채팅 <메시지>"));
        } else if (name.equals("팀 채팅 보내기")) {
            player.sendMessage(message(messages, "chat-menu-team-usage", "사용법: /섬 팀채팅 <메시지>"));
        } else if (name.equals("최근 섬 로그")) {
            player.performCommand("섬 로그");
        } else if (name.equals("섬 설정")) {
            player.performCommand("섬 설정");
        } else if (name.equals("메인 메뉴")) {
            player.performCommand("섬 메뉴");
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
