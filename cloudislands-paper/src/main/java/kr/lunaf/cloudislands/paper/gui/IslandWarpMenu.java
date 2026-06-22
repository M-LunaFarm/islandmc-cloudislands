package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.WarpView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandWarpMenu implements Listener {
    private static final String TITLE = "섬 워프 관리";
    private static final String PUBLIC_TITLE = "공개 섬 워프";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/warps.yml",
        new GuiMenuDefinition("island.warps", 6, "menu.warps.title", Map.of(
            "open", "island.warps.open",
            "teleport", "island.warp.teleport",
            "public-toggle", "island.warp.public.toggle",
            "public", "island.warp.public",
            "private", "island.warp.private",
            "delete-prepare", "island.warp.delete.prepare",
            "delete-confirm", ConfirmationTokenPolicy.WARP_DELETE_CONFIRM_ACTION,
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final GuiMenuDefinition PUBLIC_MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/public-warps.yml",
        new GuiMenuDefinition("island.public-warps", 6, "menu.public-warps.title", Map.of(
            "open", "island.visit.public.open",
            "category", "island.visit.public.category",
            "teleport", "island.warp.teleport",
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final String PUBLIC_MENU_ID = PUBLIC_MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandWarpMenu() {
        this(null);
    }

    public IslandWarpMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandWarpMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static org.bukkit.Material deleteConfirmationMaterial() {
        return GuiMenuRenderer.material(MENU, "_", "ENDER_PEARL");
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandWarps(client, islandId)
            .thenAccept(warps -> openSync(plugin, player, session, TITLE, warps, false, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "warp-menu-load-failed", "섬 워프를 불러오지 못했습니다."), "island.warps.open", "island.settings.open");
                return null;
            });
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player) {
        openPublic(plugin, client, player, null);
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        openPublic(plugin, client, player, messages, "", "");
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, String category, String query) {
        GuiSession session = GuiSessions.begin(player, PUBLIC_MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, PUBLIC_MENU.titleKey(), PUBLIC_TITLE));
        PaperGuiViews.publicWarps(client, 45, category, query)
            .thenAccept(warps -> openSync(plugin, player, session, PUBLIC_TITLE, warps, true, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, PUBLIC_MENU.titleKey(), PUBLIC_TITLE), message(messages, "warp-menu-public-load-failed", "공개 섬 워프를 불러오지 못했습니다."), "island.visit.public.open", "island.visit.open");
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
        if (slot < 0 || slot >= (publicMenu ? PUBLIC_MENU : MENU).size()) {
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        if (!publicMenu && data.getOrDefault("mode", "").equals("set-current")) {
            player.closeInventory();
            player.sendMessage(message(messages, "warp-menu-set-usage", "사용법: /섬 워프설정 <이름>"));
            return;
        }
        String warpName = data.getOrDefault("warpName", "");
        if (warpName.isBlank()) {
            String actionId = GuiItems.actionId(event.getCurrentItem());
            if (actionId.isBlank()) {
                return;
            }
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
            return;
        }
        player.closeInventory();
        String islandId = data.getOrDefault("islandId", "");
        if (publicMenu && !islandId.isBlank()) {
            actions.execute(player, new GuiAction.WarpTeleport(warpName, UUID.fromString(islandId)), GuiClick.from(event));
            return;
        }
        if (event.isShiftClick() && event.isRightClick()) {
            actions.execute(player, new GuiAction.WarpDelete(GuiAction.WarpDeleteType.PREPARE, warpName, ""), GuiClick.from(event));
            return;
        }
        if (event.isRightClick()) {
            boolean publicAccess = Boolean.parseBoolean(data.getOrDefault("publicAccess", "false"));
            actions.execute(player, new GuiAction.WarpAccess(GuiAction.WarpAccessType.TOGGLE, warpName, publicAccess), GuiClick.from(event));
            return;
        }
        actions.execute(player, new GuiAction.WarpTeleport(warpName, null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String title, List<WarpView> warps, boolean publicMenu, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            GuiMenuDefinition menu = publicMenu ? PUBLIC_MENU : MENU;
            Inventory inventory = GuiMenuRenderer.render(menu, session, messages, title, item -> !"_".equals(item.symbol()));
            List<Integer> warpSlots = GuiMenuRenderer.slots(menu, "_");
            List<WarpView> visibleWarps = warps.stream().limit(warpSlots.size()).toList();
            for (int index = 0; index < visibleWarps.size(); index++) {
                inventory.setItem(warpSlots.get(index), warpItem(visibleWarps.get(index), publicMenu, messages));
            }
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static ItemStack warpItem(WarpView warp, boolean publicMenu, MessageRenderer messages) {
        GuiMenuDefinition menu = publicMenu ? PUBLIC_MENU : MENU;
        org.bukkit.Material material = GuiMenuRenderer.material(menu, warp.publicAccess() ? "PUBLIC" : "PRIVATE", "PRIVATE", "ENDER_PEARL");
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

}
