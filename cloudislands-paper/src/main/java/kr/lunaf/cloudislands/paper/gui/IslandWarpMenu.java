package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.WarpView;
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

public final class IslandWarpMenu implements Listener {
    private static final String TITLE = "섬 워프 관리";
    private static final String PUBLIC_TITLE = "공개 섬 워프";
    private static final String MENU_ID = "island.warps";
    private static final String PUBLIC_MENU_ID = "island.public-warps";
    private final MessageRenderer messages;

    public IslandWarpMenu() {
        this(null);
    }

    public IslandWarpMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, TITLE);
        PaperGuiViews.islandWarps(client, islandId)
            .thenAccept(warps -> openSync(plugin, player, TITLE, warps, false, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, TITLE, message(messages, "warp-menu-load-failed", "섬 워프를 불러오지 못했습니다."), "island.warps.open", "island.settings.open");
                return null;
            });
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player) {
        openPublic(plugin, client, player, null);
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, PUBLIC_TITLE);
        PaperGuiViews.publicWarps(client, 45)
            .thenAccept(warps -> openSync(plugin, player, PUBLIC_TITLE, warps, true, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, PUBLIC_TITLE, message(messages, "warp-menu-public-load-failed", "공개 섬 워프를 불러오지 못했습니다."), "island.visit.public.open", "island.visit.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        boolean publicMenu = GuiInventories.isMenu(event.getView().getTopInventory(), PUBLIC_MENU_ID);
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID) && !publicMenu) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (!publicMenu && slot == 45) {
            player.sendMessage(message(messages, "warp-menu-set-usage", "사용법: /섬 워프설정 <이름>"));
            return;
        }
        if (publicMenu && slot == 45) {
            GuiActionRegistry.execute(player, "island.visit.public.open", GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String warpName = data.getOrDefault("warpName", "");
        if (warpName.isBlank()) {
            return;
        }
        String islandId = data.getOrDefault("islandId", "");
        if (publicMenu && !islandId.isBlank()) {
            GuiActionRegistry.execute(player, "island.warp.teleport", java.util.Map.of("islandId", String.valueOf(islandId), "warpName", warpName), GuiClick.from(event));
            return;
        }
        if (event.isShiftClick() && event.isRightClick()) {
            GuiActionRegistry.execute(player, "island.warp.delete.prepare", java.util.Map.of("warpName", warpName), GuiClick.from(event));
            return;
        }
        if (event.isRightClick()) {
            boolean publicAccess = Boolean.parseBoolean(data.getOrDefault("publicAccess", "false"));
            GuiActionRegistry.execute(player, "island.warp.public.toggle", java.util.Map.of("warpName", warpName, "publicAccess", String.valueOf(publicAccess)), GuiClick.from(event));
            return;
        }
        GuiActionRegistry.execute(player, "island.warp.teleport", java.util.Map.of("warpName", warpName), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, String title, List<WarpView> warps, boolean publicMenu, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(publicMenu ? PUBLIC_MENU_ID : MENU_ID, 54, title);
            inventory.setItem(45, publicMenu
                ? item(Material.COMPASS, message(messages, "warp-menu-public-refresh-name", "공개 워프 새로고침"), message(messages, "warp-menu-public-refresh-command", "/섬 공개워프목록"))
                : item(Material.ENDER_PEARL, message(messages, "warp-menu-set-current-name", "현재 위치를 워프로 설정"), message(messages, "warp-menu-set-usage", "사용법: /섬 워프설정 <이름>")));
            int slot = 0;
            for (WarpView warp : warps.stream().limit(45).toList()) {
                inventory.setItem(slot++, warpItem(warp, publicMenu, messages));
            }
            inventory.setItem(49, item(Material.COMPARATOR, message(messages, "warp-menu-settings-name", "설정"), message(messages, "warp-menu-settings-command", "/섬 설정")));
            inventory.setItem(53, item(Material.COMPASS, message(messages, "warp-menu-main-menu-name", "메인 메뉴"), message(messages, "warp-menu-main-menu-command", "/섬 메뉴")));
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

    private static ItemStack warpItem(WarpView warp, boolean publicMenu, MessageRenderer messages) {
        Material material = warp.publicAccess() ? Material.ENDER_EYE : Material.ENDER_PEARL;
        if (publicMenu) {
            return GuiItems.action(material, warp.name(), "island.warp.teleport",
                Map.of("islandId", warp.islandId(), "warpName", warp.name()),
                message(messages, "warp-menu-category", "카테고리: ") + (warp.category().isBlank() ? "default" : warp.category()),
                message(messages, "warp-menu-location", "위치: ") + (long) warp.x() + ", " + (long) warp.y() + ", " + (long) warp.z(),
                message(messages, "warp-menu-public-left-click", "좌클릭: 공개 워프로 이동"));
        }
        return GuiItems.action(material, warp.name(), "island.warp.teleport",
            Map.of("warpName", warp.name(), "publicAccess", String.valueOf(warp.publicAccess())),
            message(messages, "warp-menu-public-state", "공개 상태: ") + (warp.publicAccess() ? message(messages, "warp-menu-public", "공개") : message(messages, "warp-menu-private", "비공개")),
            message(messages, "warp-menu-category", "카테고리: ") + (warp.category().isBlank() ? "default" : warp.category()),
            message(messages, "warp-menu-location", "위치: ") + (long) warp.x() + ", " + (long) warp.y() + ", " + (long) warp.z(),
            warp.publicAccess() ? message(messages, "warp-menu-public-label", "공개 워프") : message(messages, "warp-menu-private-label", "비공개 워프"),
            message(messages, "warp-menu-left-click", "좌클릭: 이동"),
            message(messages, "warp-menu-toggle-click", "우클릭: 공개/비공개 전환"),
            message(messages, "warp-menu-delete-click", "Shift+우클릭: 삭제"));
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
