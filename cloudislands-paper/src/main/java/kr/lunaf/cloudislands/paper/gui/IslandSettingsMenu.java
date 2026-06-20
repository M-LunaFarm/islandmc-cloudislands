package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
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

public final class IslandSettingsMenu implements Listener {
    private static final String MENU_ID = "island.settings";
    private static final String TITLE_KEY = "settings-menu-title";
    private static final String TITLE = "섬 설정";
    private final MessageRenderer messages;

    public IslandSettingsMenu() {
        this(null);
    }

    public IslandSettingsMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, java.util.UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, java.util.UUID islandId, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandInfo(client, islandId)
            .thenAccept(view -> openSync(plugin, player, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, message(messages, TITLE_KEY, TITLE), message(messages, "settings-menu-load-failed", "섬 설정을 불러오지 못했습니다."), "island.settings.open", "island.main.open");
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, IslandInfoView view, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            boolean publicAccess = view.publicAccess();
            boolean locked = view.locked();
            Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(10, item(publicAccess ? Material.LIME_DYE : Material.GRAY_DYE, message(messages, "settings-menu-public-name", "공개 설정"), message(messages, "settings-menu-current", "현재: ") + (publicAccess ? message(messages, "settings-menu-public", "공개") : message(messages, "settings-menu-private", "비공개")), message(messages, "settings-menu-public-left-click", "좌클릭: /섬 공개"), message(messages, "settings-menu-public-right-click", "우클릭: /섬 비공개")));
            inventory.setItem(11, item(locked ? Material.IRON_DOOR : Material.OAK_DOOR, message(messages, "settings-menu-lock-name", "잠금 설정"), message(messages, "settings-menu-current", "현재: ") + (locked ? message(messages, "settings-menu-locked", "잠김") : message(messages, "settings-menu-open", "열림")), message(messages, "settings-menu-lock-left-click", "좌클릭: /섬 잠금해제"), message(messages, "settings-menu-lock-right-click", "우클릭: /섬 잠금")));
            inventory.setItem(12, item(Material.NAME_TAG, message(messages, "settings-menu-member-name", "멤버 관리"), message(messages, "settings-menu-member-command", "/섬 멤버")));
            inventory.setItem(13, item(Material.COMPARATOR, message(messages, "settings-menu-permission-name", "권한 설정"), message(messages, "settings-menu-permission-command", "/섬 권한")));
            inventory.setItem(14, item(Material.REDSTONE_TORCH, message(messages, "settings-menu-flag-name", "플래그 설정"), message(messages, "settings-menu-flag-command", "/섬 플래그")));
            inventory.setItem(15, item(Material.ENDER_PEARL, message(messages, "settings-menu-warp-name", "워프 관리"), message(messages, "settings-menu-warp-command", "/섬 워프")));
            inventory.setItem(16, item(Material.BARRIER, message(messages, "settings-menu-ban-name", "방문자 밴"), message(messages, "settings-menu-ban-command", "/섬 밴목록")));
            inventory.setItem(17, item(Material.NAME_TAG, message(messages, "settings-menu-role-name", "역할 설정"), message(messages, "settings-menu-role-command", "/섬 역할")));
            inventory.setItem(18, item(Material.GOLD_BLOCK, message(messages, "settings-menu-bank-name", "은행"), message(messages, "settings-menu-bank-command", "/섬 은행")));
            inventory.setItem(19, item(Material.BEACON, message(messages, "settings-menu-upgrade-name", "업그레이드"), message(messages, "settings-menu-upgrade-command", "/섬 업그레이드")));
            inventory.setItem(20, item(Material.GRASS_BLOCK, message(messages, "settings-menu-biome-name", "바이옴"), message(messages, "settings-menu-biome-command", "/섬 바이옴")));
            inventory.setItem(21, item(Material.HOPPER, message(messages, "settings-menu-limit-name", "제한"), message(messages, "settings-menu-limit-command", "/섬 제한")));
            inventory.setItem(22, item(Material.CHEST, message(messages, "settings-menu-snapshot-name", "스냅샷"), message(messages, "settings-menu-snapshot-command", "/섬 스냅샷")));
            inventory.setItem(23, item(Material.MAP, message(messages, "settings-menu-info-name", "섬 정보"), message(messages, "settings-menu-info-command", "/섬 정보")));
            inventory.setItem(26, item(Material.TNT, message(messages, "settings-menu-danger-name", "위험 작업"), message(messages, "settings-menu-danger-command", "/섬 위험작업")));
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
        if (slot < 0 || slot >= 27) {
            return;
        }
        player.closeInventory();
        if (slot == 10) {
            GuiActionRegistry.execute(player, "island.public.toggle", GuiClick.from(event));
        } else if (slot == 11) {
            GuiActionRegistry.execute(player, "island.lock.toggle", GuiClick.from(event));
        } else if (slot == 12) {
            GuiActionRegistry.execute(player, "island.members.open", GuiClick.from(event));
        } else if (slot == 13) {
            GuiActionRegistry.execute(player, "island.permissions.open", GuiClick.from(event));
        } else if (slot == 14) {
            GuiActionRegistry.execute(player, "island.flags.open", GuiClick.from(event));
        } else if (slot == 15) {
            GuiActionRegistry.execute(player, "island.warps.open", GuiClick.from(event));
        } else if (slot == 16) {
            GuiActionRegistry.execute(player, "island.bans.open", GuiClick.from(event));
        } else if (slot == 17) {
            GuiActionRegistry.execute(player, "island.roles.open", GuiClick.from(event));
        } else if (slot == 18) {
            GuiActionRegistry.execute(player, "island.bank.open", GuiClick.from(event));
        } else if (slot == 19) {
            GuiActionRegistry.execute(player, "island.upgrades.open", GuiClick.from(event));
        } else if (slot == 20) {
            GuiActionRegistry.execute(player, "island.biome.open", GuiClick.from(event));
        } else if (slot == 21) {
            GuiActionRegistry.execute(player, "island.limits.open", GuiClick.from(event));
        } else if (slot == 22) {
            GuiActionRegistry.execute(player, "island.snapshots.open", GuiClick.from(event));
        } else if (slot == 23) {
            GuiActionRegistry.execute(player, "island.info.open", GuiClick.from(event));
        } else if (slot == 26) {
            GuiActionRegistry.execute(player, "island.danger.open", GuiClick.from(event));
        }
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
