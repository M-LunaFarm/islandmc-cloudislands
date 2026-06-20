package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.IslandInfoView;
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

public final class IslandInfoMenu implements Listener {
    private static final String MENU_ID = "island.info";
    private static final String TITLE_KEY = "info-menu-title";
    private static final String TITLE = "섬 정보";
    private final MessageRenderer messages;

    public IslandInfoMenu() {
        this(null);
    }

    public IslandInfoMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandInfo(client, islandId)
            .thenAccept(view -> openSync(plugin, player, session, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "info-menu-load-failed", "섬 정보를 불러오지 못했습니다."), "island.info.open", "island.main.open");
                return null;
            });
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
        player.closeInventory();
        if (slot == 23) {
            GuiActionRegistry.execute(player, "island.level.recalculate", GuiClick.from(event));
        } else if (slot == 21) {
            GuiActionRegistry.execute(player, "island.ranking.open", GuiClick.from(event));
        } else if (slot == 22) {
            GuiActionRegistry.execute(player, "island.logs.open", GuiClick.from(event));
        } else if (slot == 16) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
        } else if (slot == 25) {
            GuiActionRegistry.execute(player, "island.info.open", GuiClick.from(event));
        } else if (slot == 24) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
        } else if (slot == 26) {
            return;
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, IslandInfoView view, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(10, item(Material.GRASS_BLOCK, message(messages, "info-menu-basic-title", "기본 정보"), message(messages, "info-menu-island-name", "섬 이름: ") + fallback(view.name(), message(messages, "info-menu-no-name", "이름 없음")), message(messages, "info-menu-state", "상태: ") + fallback(view.state(), message(messages, "info-menu-unknown", "알 수 없음")), message(messages, "info-menu-island-id", "섬 ID: ") + shortId(view.islandId(), messages)));
            inventory.setItem(11, item(Material.EXPERIENCE_BOTTLE, message(messages, "info-menu-level-title", "레벨"), message(messages, "info-menu-level", "레벨: ") + view.level(), message(messages, "info-menu-worth", "가치: ") + fallback(view.worth(), "0")));
            inventory.setItem(12, item(Material.BARRIER, message(messages, "info-menu-access-title", "공개 상태"), message(messages, "info-menu-public-access", "공개 여부: ") + yesNo(view.publicAccess(), messages), message(messages, "info-menu-locked", "잠금 여부: ") + yesNo(view.locked(), messages)));
            inventory.setItem(13, item(Material.MAP, message(messages, "info-menu-size-title", "크기와 경계"), message(messages, "info-menu-size", "섬 크기: ") + view.size(), message(messages, "info-menu-border", "경계: ") + view.border()));
            inventory.setItem(14, item(Material.PLAYER_HEAD, message(messages, "info-menu-owner-title", "소유자"), message(messages, "info-menu-owner", "소유자: ") + shortId(view.ownerUuid(), messages)));
            inventory.setItem(16, item(Material.REDSTONE_TORCH, message(messages, "info-menu-settings-name", "설정"), message(messages, "info-menu-settings-command", "/섬 설정")));
            inventory.setItem(21, item(Material.GOLD_BLOCK, message(messages, "info-menu-ranking-name", "섬 랭킹"), message(messages, "info-menu-ranking-command", "/섬 랭킹")));
            inventory.setItem(22, item(Material.CLOCK, message(messages, "info-menu-log-name", "섬 로그"), message(messages, "info-menu-log-command", "/섬 로그")));
            inventory.setItem(23, item(Material.ANVIL, message(messages, "info-menu-recalculate-name", "레벨 다시 계산"), message(messages, "info-menu-recalculate-command", "/섬 레벨계산")));
            inventory.setItem(24, item(Material.COMPASS, message(messages, "info-menu-main-menu-name", "메인 메뉴"), message(messages, "info-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(25, item(Material.CLOCK, message(messages, "info-menu-refresh-name", "새로고침"), message(messages, "info-menu-refresh-command", "/섬 정보")));
            inventory.setItem(26, item(Material.OAK_DOOR, message(messages, "info-menu-close-name", "닫기"), message(messages, "info-menu-close", "메뉴를 닫습니다.")));
            player.openInventory(inventory);
        });
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

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String yesNo(boolean value, MessageRenderer messages) {
        return value ? message(messages, "info-menu-yes", "예") : message(messages, "info-menu-no", "아니오");
    }

    private static String shortId(String value, MessageRenderer messages) {
        if (value == null || value.isBlank()) {
            return message(messages, "info-menu-unknown", "알 수 없음");
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
