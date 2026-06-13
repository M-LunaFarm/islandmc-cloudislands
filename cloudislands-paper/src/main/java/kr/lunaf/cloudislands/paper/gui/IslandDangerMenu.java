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

public final class IslandDangerMenu implements Listener {
    private static final String TITLE = "섬 위험 작업";
    private final MessageRenderer messages;

    public IslandDangerMenu() {
        this(null);
    }

    public IslandDangerMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.CHEST, "스냅샷 확인", "/섬 스냅샷", "위험 작업 전에 복구 지점을 확인합니다."));
        inventory.setItem(12, item(Material.TNT, "섬 리셋 확인", "월드를 초기화하고 복구 작업을 요청합니다.", message(messages, "danger-confirm-required", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(14, item(Material.LAVA_BUCKET, "섬 삭제 확인", "섬 삭제 작업을 요청합니다.", message(messages, "danger-confirm-required", "Shift+우클릭해야 실행됩니다.")));
        inventory.setItem(22, item(Material.OAK_DOOR, "돌아가기", "/섬 설정"));
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
        if (name.equals("돌아가기")) {
            player.performCommand("섬 설정");
            return;
        }
        if (name.equals("스냅샷 확인")) {
            player.performCommand("섬 스냅샷");
            return;
        }
        if (!event.isShiftClick() || !event.isRightClick()) {
            player.sendMessage(message(messages, "danger-confirm-click-required", "위험 작업은 Shift+우클릭해야 실행됩니다."));
            return;
        }
        if (name.equals("섬 리셋 확인")) {
            player.performCommand("섬 리셋 confirm");
        } else if (name.equals("섬 삭제 확인")) {
            player.performCommand("섬 삭제 confirm");
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
