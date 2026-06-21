package kr.lunaf.cloudislands.paper.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiSystemPolicyTest {
    @Test
    void pinsMainMenuButtonsFromGoal() {
        assertEquals(
                List.of(
                        "my-island-home",
                        "create-island",
                        "visit-island",
                        "member-management",
                        "permission-settings",
                        "island-upgrades",
                        "warp-management",
                        "island-ranking",
                        "missions",
                        "admin-menu"
                ),
                GuiSystemPolicy.mainMenuButtons()
        );
        assertTrue(GuiSystemPolicy.mainMenuButton("my-island-home"));
        assertTrue(GuiSystemPolicy.mainMenuButton("admin-menu"));
    }

    @Test
    void pinsMemberManagementFields() {
        for (String field : List.of(
                "member-list",
                "invite",
                "kick",
                "promote",
                "demote",
                "transfer-ownership",
                "online-state",
                "last-seen-at"
        )) {
            assertTrue(GuiSystemPolicy.memberMenuFields().contains(field), field);
        }
    }

    @Test
    void pinsPermissionMatrixShape() {
        assertEquals(List.of("CORE_ROLE_CATALOG", "VISITOR_FALLBACK"), GuiSystemPolicy.permissionMatrixRoles());
        assertEquals(List.of("IslandPermission.values()"), GuiSystemPolicy.permissionMatrixColumns());
        assertEquals(List.of("PaperGuiViews.islandRoles", "VISITOR_FALLBACK"), GuiSystemPolicy.permissionMatrix().get("role-source"));
        assertEquals(List.of("IslandPermission.values()"), GuiSystemPolicy.permissionMatrix().get("permission-source"));
        assertEquals(List.of("stage", "save-batch"), GuiSystemPolicy.permissionMatrix().get("write-mode"));
    }

    @Test
    void pinsNodeAdminDashboardFieldsAndActions() {
        assertEquals(List.of("node-id", "players", "mspt", "active-islands", "queue", "state"), GuiSystemPolicy.nodeAdminFields());
        for (String action : List.of("Drain", "Undrain", "View Islands", "Move Load", "Shutdown Safe")) {
            assertTrue(GuiSystemPolicy.nodeAdminAction(action), action);
        }
    }

    @Test
    void actionRegistryRejectsUnsupportedClicks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActionRegistry.java"));
        assertTrue(source.contains("GuiClick.UNSUPPORTED"), "null clicks must not be treated as LEFT");
        assertTrue(source.contains("!safeClick.supported()"), "unsupported clicks must be dropped before action execution");
        assertTrue(source.contains("GuiActionParser.parse(actionId, data)"), "GUI actions must pass through typed parser before execution");
        assertTrue(source.contains("dedupePolicy.accept"), "rapid duplicate GUI actions must be dropped before executor dispatch");
        assertTrue(source.contains("executor.execute(player, action, safeClick)"), "parsed GUI actions must be executed as typed action objects");
        assertTrue(source.contains("private final GuiActionExecutor executor"), "GUI action executor must be constructor-injected");
        assertTrue(source.contains("private final GuiActionDedupePolicy dedupePolicy"), "GUI action registry must own the per-player duplicate-action guard");
        assertFalse(source.contains("AtomicReference"), "GUI action registry must not keep global mutable executor state");
        assertFalse(source.contains("static void configure"), "GUI action registry must not be reconfigured globally");
    }

    @Test
    void unsupportedInventoryClickModesDoNotExecuteGuiActions() throws Exception {
        String click = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiClick.java"));
        String clickPolicy = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiClickPolicy.java"));
        String registry = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActionRegistry.java"));

        assertTrue(click.contains("static GuiClick fromClickType(ClickType click)"), "click-type mapping must stay isolated for policy verification");
        assertTrue(click.contains("GuiClickPolicy.fromClickName"), "Bukkit click types must flow through the testable click policy");
        assertTrue(clickPolicy.contains("clickName == null || clickName.isBlank()"), "null click events must be treated as unsupported");
        assertTrue(clickPolicy.contains("default -> GuiClick.UNSUPPORTED"), "number-key, drop, double-click, and offhand clicks must stay unsupported by default");
        assertFalse(click.contains("case NUMBER_KEY"), "hotbar number-key swaps must not execute GUI actions");
        assertFalse(click.contains("case DROP"), "drop clicks must not execute GUI actions");
        assertFalse(click.contains("case SWAP_OFFHAND"), "offhand swaps must not execute GUI actions");
        assertTrue(registry.contains("!safeClick.supported()"), "unsupported clicks must be dropped before action parsing");
    }

    @Test
    void menuDragEventsCannotWriteIntoTopInventory() throws Exception {
        String guard = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiEventGuard.java"));
        String policy = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiInventoryEventPolicy.java"));
        String registrar = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandGuiMenuRegistrar.java"));

        assertTrue(registrar.contains("new GuiEventGuard()"), "GUI event guard must be registered with menu listeners");
        assertTrue(guard.contains("InventoryDragEvent"), "drag events must be guarded globally");
        assertTrue(guard.contains("top.getHolder() instanceof CloudIslandsMenuHolder"), "drag guard must only apply to CloudIslands menus");
        assertTrue(guard.contains("GuiInventoryEventPolicy.cancelDrag(true, event.getRawSlots(), top.getSize())"), "drag guard must delegate raw slot decisions to the tested policy");
        assertTrue(guard.contains("event.setCancelled(true)"), "dragging into a GUI top inventory must be cancelled");
        assertTrue(policy.contains("rawSlot >= 0 && rawSlot < topSize"), "top inventory slot detection must use raw slot bounds");
    }

    @Test
    void cloudMenuClicksAreCancelledBeforeMenuSpecificActions() throws Exception {
        String guard = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiEventGuard.java"));
        String items = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiItems.java"));
        String policy = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiInventoryEventPolicy.java"));

        assertTrue(guard.contains("InventoryClickEvent"), "GUI click events must be guarded globally");
        assertTrue(guard.contains("event.getView().getTopInventory()"), "click guard must inspect the open top inventory");
        assertTrue(guard.contains("top.getHolder() instanceof CloudIslandsMenuHolder"), "click guard must only apply to CloudIslands menus");
        assertTrue(guard.contains("GuiInventoryEventPolicy.cancelClick"), "click guard must delegate cancellation decisions to the tested policy");
        assertTrue(guard.contains("event.setCancelled(true)"), "CloudIslands GUI clicks, including player-inventory clicks, must be cancelled");
        assertTrue(items.contains("GuiInventoryEventPolicy.acceptsMenuActionSlot"), "menu actions must route slot decisions through the tested policy");
        assertTrue(items.contains("GuiClick.from(event)"), "menu action slot checks must include the Bukkit click type");
        assertTrue(policy.contains("click.supported() && clickedTopInventory && rawSlot >= 0 && rawSlot < topSize"), "menu actions must only execute for supported clicks on top inventory raw slots");
    }

    @Test
    void executorBoundaryUsesTypedActions() throws Exception {
        String executor = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActionExecutor.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiAction.java"));
        String controller = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"));
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));

        assertTrue(executor.contains("void execute(Player player, GuiAction action, GuiClick click)"), "executor boundary must not expose raw action id and payload map");
        assertFalse(executor.contains("String actionId, Map<String, String> data"), "executor boundary must receive parsed GuiAction objects");
        assertFalse(actions.contains("record SnapshotRestore(String actionId, long snapshotNo, Map<String, String> data)"), "snapshot restore GUI actions must not carry raw action id and payload maps internally");
        assertTrue(actions.contains("record SnapshotRestore(SnapshotRestoreType type, long snapshotNo, String confirmationToken)"), "snapshot restore GUI actions must use typed state plus an explicit confirmation token");
        assertFalse(actions.contains("record WarpDelete(String actionId, String warpName, Map<String, String> data)"), "warp delete GUI actions must not carry raw action id and payload maps internally");
        assertTrue(actions.contains("record WarpDelete(WarpDeleteType type, String warpName, String confirmationToken)"), "warp delete GUI actions must use typed state plus an explicit confirmation token");
        assertFalse(actions.contains("record MemberRoleChange(String actionId, UUID playerUuid, Map<String, String> data)"), "member role GUI actions must not carry raw action id and payload maps internally");
        assertTrue(actions.contains("record MemberRoleChange(MemberRoleChangeType type, UUID playerUuid, String confirmationToken)"), "member role GUI actions must use typed state plus an explicit confirmation token");
        assertFalse(actions.contains("record BanPardon(String actionId, UUID playerUuid, Map<String, String> data)"), "ban pardon GUI actions must not carry raw action id and payload maps internally");
        assertTrue(actions.contains("record BanPardon(BanPardonType type, UUID playerUuid, String confirmationToken)"), "ban pardon GUI actions must use typed state plus an explicit confirmation token");
        assertFalse(actions.contains("record MemberRemoval(String actionId, UUID playerUuid, Map<String, String> data)"), "member removal GUI actions must not carry raw action id and payload maps internally");
        assertTrue(actions.contains("record MemberRemoval(MemberRemovalType type, UUID playerUuid, String confirmationToken)"), "member removal GUI actions must use typed state plus an explicit confirmation token");
        assertTrue(controller.contains("backend.executeGuiAction(player, action, click)"), "command controller must forward typed GUI actions");
        assertTrue(backend.contains("void executeGuiAction(Player player, GuiAction action, GuiClick click)"), "command backend must accept typed GUI actions");
    }

    @Test
    void menuRegistrarInjectsGuiActionRegistry() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandGuiMenuRegistrar.java"));
        assertTrue(source.contains("new GuiActionRegistry(executor)"), "menu bootstrap must create the action registry instance");
        assertTrue(source.contains("GuiStateMenus.listener(registry)"), "state menus must share the injected action registry");
        assertFalse(source.contains("GuiActionRegistry.configure"), "menu bootstrap must not configure global registry state");
    }

    @Test
    void infoMenuStaticPanelsRenderFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandInfoMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/info.yml"));

        assertTrue(config.contains("material: GRASS_BLOCK"), "info menu basic panel material must live in config-v2");
        assertTrue(config.contains("material: EXPERIENCE_BOTTLE"), "info menu level panel material must live in config-v2");
        assertTrue(config.contains("material: PLAYER_HEAD"), "info menu owner panel material must live in config-v2");
        assertTrue(menu.contains("MENU.itemAt(slot)"), "info menu dynamic panels must render the configured menu item");
        assertTrue(menu.contains("GuiMenuRenderer.item(MENU, item, messages"), "info menu panels must use the shared config-backed renderer");
        assertFalse(menu.contains("Material.GRASS_BLOCK"), "info menu must not hard-code the basic panel material");
        assertFalse(menu.contains("Material.EXPERIENCE_BOTTLE"), "info menu must not hard-code the level panel material");
        assertFalse(menu.contains("Material.PLAYER_HEAD"), "info menu must not hard-code the owner panel material");
    }

    @Test
    void settingsMenuToggleMaterialsRenderFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandSettingsMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/settings.yml"));

        assertTrue(config.contains("active-material: LIME_DYE"), "public enabled material must live in config-v2");
        assertTrue(config.contains("inactive-material: GRAY_DYE"), "public disabled material must live in config-v2");
        assertTrue(config.contains("active-material: IRON_DOOR"), "locked material must live in config-v2");
        assertTrue(menu.contains("GuiMenuRenderer.stateItem(MENU, item, messages"), "settings toggles must render stateful configured menu items");
        assertFalse(menu.contains("Material.LIME_DYE"), "settings menu must not hard-code public enabled material");
        assertFalse(menu.contains("Material.GRAY_DYE"), "settings menu must not hard-code public disabled material");
        assertFalse(menu.contains("Material.IRON_DOOR"), "settings menu must not hard-code locked material");
        assertFalse(menu.contains("Material.OAK_DOOR"), "settings menu must not hard-code unlocked material");
    }

    @Test
    void bankBalancePanelRendersFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandBankMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/bank.yml"));

        assertTrue(config.contains("material: GOLD_BLOCK"), "bank balance material must live in config-v2");
        assertTrue(config.contains("name-key: bank-menu-balance-name"), "bank balance label must live in config-v2");
        assertTrue(menu.contains("MENU.itemAt(4)"), "bank balance panel must render the configured menu item");
        assertTrue(menu.contains("GuiMenuRenderer.item(MENU, item, messages"), "bank balance panel must use the shared config-backed renderer");
        assertFalse(menu.contains("Material.GOLD_BLOCK"), "bank menu must not hard-code the balance material");
    }

    @Test
    void guiSessionsAreRevisionGuardedAndClearedOnPluginDisable() throws Exception {
        String sessions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiSessions.java"));
        String guard = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiEventGuard.java"));
        String inventories = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiInventories.java"));
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));

        assertTrue(sessions.contains("AtomicLong REVISIONS"), "GUI sessions must carry a monotonically increasing revision");
        assertTrue(sessions.contains("CURRENT.put(player.getUniqueId(), session)"), "opening a GUI must replace the current player session");
        assertTrue(sessions.contains("session.equals(CURRENT.get(player.getUniqueId()))"), "delayed GUI responses must check the current player session");
        assertTrue(sessions.contains("runIfCurrent"), "async GUI rendering must be guarded by the current session");
        assertTrue(sessions.contains("CURRENT.clear()"), "GUI sessions must expose a lifecycle cleanup hook");
        assertTrue(inventories.contains("create(String menuId, GuiSession session"), "async-rendered GUI inventories must carry the current session id");
        assertTrue(guard.contains("InventoryCloseEvent"), "all CloudIslands GUI closes must invalidate the current session");
        assertTrue(guard.contains("GuiSessions.invalidate(player, menuHolder.sessionId())"), "GUI close invalidation must target the holder session id");
        assertTrue(guard.contains("PlayerQuitEvent"), "player disconnects must invalidate the current GUI session");
        assertTrue(guard.contains("PlayerChangedWorldEvent"), "world changes must invalidate the current GUI session");
        assertTrue(guard.contains("GuiSessions.invalidate(event.getPlayer())"), "player lifecycle invalidation must clear the current player session");
        assertTrue(plugin.contains("GuiSessions.clear()"), "plugin disable must clear stale GUI sessions");
    }
}
