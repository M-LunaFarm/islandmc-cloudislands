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
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActions.java"));
        assertTrue(source.contains("GuiClick.UNSUPPORTED"), "null clicks must not be treated as LEFT");
        assertTrue(source.contains("!safeClick.supported()"), "unsupported clicks must be dropped before action execution");
        assertTrue(source.contains("void execute(Player player, GuiAction action, GuiClick click)"), "registry boundary must receive typed GUI actions");
        assertFalse(source.contains("String actionId, Map<String, String> data"), "registry boundary must not expose raw action id and payload map");
        assertTrue(actions.contains("GuiActionParser.parse(actionId, data)"), "PDC action data must pass through typed parser before registry execution");
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
        assertFalse(actions.contains("record BankAmount(String actionId, BigDecimal amount)"), "bank amount GUI actions must not carry raw action ids internally");
        assertTrue(actions.contains("record BankAmount(BankAmountType type, BigDecimal amount)"), "bank amount GUI actions must use typed deposit/withdraw state");
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
    void mainMenuAlternateClickActionsComeFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMainMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/main.yml"));

        assertTrue(config.contains("rightAction: island.visit.random"), "main menu right-click alternate action must live in config-v2");
        assertTrue(menu.contains("data.getOrDefault(\"rightAction\", actionId)"), "main menu must read alternate click actions from item data");
        assertTrue(menu.contains("GuiActions.from(actionId, data)"), "main menu alternate actions must still pass through the typed action parser");
        assertFalse(menu.contains("actionId.equals(\"island.visit.open\")"), "main menu must not hard-code the visit item as a special action branch");
        assertFalse(menu.contains("GuiAction.NoPayloadType.VISIT_RANDOM"), "main menu must not hard-code the random visit action type");
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
    void memberMenuSurfacesTemporaryTrustExpiryAndHelp() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMemberMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/members.yml"));

        assertTrue(config.contains("member-menu-coop-usage"), "temporary co-op trust help must live in config-v2");
        assertTrue(config.contains("/섬 협동 <플레이어> <30m|2h|1d>"), "member menu must show temporary trust syntax");
        assertTrue(menu.contains("\"expiresAt\", member.expiresAt()"), "member item action data must carry temporary trust expiry");
        assertTrue(menu.contains("expiryLine(member, messages)"), "member item lore must include temporary trust expiry");
        assertTrue(menu.contains("member-menu-temporary-trust-expires"), "temporary trust expiry copy must be localizable");
    }

    @Test
    void bankBalancePanelRendersFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandBankMenu.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/bank.yml"));

        assertTrue(config.contains("material: GOLD_BLOCK"), "bank balance material must live in config-v2");
        assertTrue(config.contains("name-key: bank-menu-balance-name"), "bank balance label must live in config-v2");
        assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, \"B\")"), "bank balance panel must render configured balance slots");
        assertTrue(menu.contains("GuiMenuRenderer.item(MENU, item, messages"), "bank balance panel must use the shared config-backed renderer");
        assertFalse(menu.contains("Material.GOLD_BLOCK"), "bank menu must not hard-code the balance material");
    }

    @Test
    void stateMenuPanelsRenderFromMenuDefinition() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiStateMenus.java"));
        String renderer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiMenuRenderer.java"));
        String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/state.yml"));

        for (String material : List.of("CLOCK", "HOPPER", "EMERALD_BLOCK", "ANVIL", "BARRIER")) {
            assertTrue(config.contains("material: " + material), "state menu material " + material + " must live in config-v2");
            assertFalse(menu.contains("Material." + material), "state menu must not hard-code " + material);
        }
        for (String stateSymbol : List.of("\"L\"", "\"S\"", "\"U\"", "\"C\"", "\"F\"", "\"N\"")) {
            assertTrue(menu.contains("stateItem(" + stateSymbol) || menu.contains("setStateItem(inventory, " + stateSymbol), "state menu must render configured state symbol " + stateSymbol);
        }
        assertTrue(renderer.contains("public static List<String> lore"), "state menu must be able to reuse configured fallback lore");
        assertTrue(menu.contains("GuiMenuRenderer.material(item.materialKey())"), "state item material must come from the menu definition");
        assertTrue(menu.contains("GuiMenuRenderer.lore(item, messages)"), "state item fallback lore must come from the menu definition");
    }

    @Test
    void emptyListPlaceholdersRenderFromMenuDefinitions() throws Exception {
        String renderer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiMenuRenderer.java"));
        assertTrue(renderer.contains("setSymbolItem(Inventory inventory"), "shared renderer must expose symbol-based empty placeholder rendering");
        assertTrue(renderer.contains("item(definition, item, messages, data, extraLore)"), "shared symbol renderer must use config-backed item rendering");
        for (String menuName : List.of(
                "IslandHomeMenu",
                "IslandBanMenu",
                "IslandVisitMenu",
                "IslandUpgradeMenu",
                "IslandMyIslandsMenu",
                "IslandInviteMenu",
                "IslandSnapshotMenu",
                "IslandLimitMenu",
                "IslandMissionMenu",
                "IslandLogMenu",
                "IslandRoleMenu"
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuName + ".java"));
            assertTrue(menu.contains("item -> !\"E\".equals(item.symbol())"), menuName + " must hide the configured empty placeholder during normal render");
            assertTrue(menu.contains("GuiMenuRenderer.setSymbolItem(inventory, MENU, \"E\""), menuName + " must render the configured empty placeholder when the list is empty");
        }
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandHomeMenu.java")).contains("home-menu-empty-title"), "home empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandBanMenu.java")).contains("ban-menu-empty-title"), "ban empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandVisitMenu.java")).contains("visit-menu-empty-title"), "visit empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandUpgradeMenu.java")).contains("upgrade-menu-empty-title"), "upgrade empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMyIslandsMenu.java")).contains("my-islands-menu-empty-title"), "my-islands empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandInviteMenu.java")).contains("invite-menu-empty-title"), "invite empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandSnapshotMenu.java")).contains("snapshot-menu-empty-title"), "snapshot empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandLimitMenu.java")).contains("limit-menu-empty-title"), "limit empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMissionMenu.java")).contains("mission-menu-empty-title"), "mission empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandLogMenu.java")).contains("log-menu-empty-title"), "log empty placeholder copy must live in config-v2");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandRoleMenu.java")).contains("role-menu-empty-title"), "role empty placeholder copy must live in config-v2");

        for (String configPath : List.of(
                "homes.yml",
                "bans.yml",
                "visit.yml",
                "upgrades.yml",
                "my-islands.yml",
                "invites.yml",
                "snapshots.yml",
                "limits.yml",
                "missions.yml",
                "logs.yml",
                "roles.yml"
        )) {
            String config = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + configPath));
            assertTrue(config.contains("  E:"), configPath + " must define the empty placeholder item");
        }
    }

    @Test
    void listItemMaterialsRenderFromMenuDefinitions() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandHomeMenu", "homes.yml", "GREEN_BED"},
                new String[] {"IslandBanMenu", "bans.yml", "BARRIER"},
                new String[] {"IslandInviteMenu", "invites.yml", "WRITABLE_BOOK"},
                new String[] {"IslandSnapshotMenu", "snapshots.yml", "PAPER"},
                new String[] {"IslandLimitMenu", "limits.yml", "HOPPER"},
                new String[] {"IslandVisitMenu", "visit.yml", "GRASS_BLOCK"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("  _:"), menuCase[1] + " must define the dynamic list item");
            assertTrue(definition.contains("material: " + menuCase[2]), menuCase[1] + " list item material must live in config-v2");
            assertTrue(menu.contains("GuiMenuRenderer.material(MENU, \"_\", \"" + menuCase[2] + "\")"), menuCase[0] + " list item must read its material from the menu definition");
            assertFalse(menu.contains("Material." + menuCase[2]), menuCase[0] + " list item must not hard-code the material");
        }
    }

    @Test
    void typedDynamicItemMaterialsRenderFromMenuDefinitions() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandMemberMenu", "members.yml", "OWNER", "NETHER_STAR"},
                new String[] {"IslandMyIslandsMenu", "my-islands.yml", "OWNER", "NETHER_STAR"},
                new String[] {"IslandRoleMenu", "roles.yml", "CUSTOM", "NAME_TAG"},
                new String[] {"IslandUpgradeMenu", "upgrades.yml", "MAX_WARPS", "ENDER_PEARL"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("  _:"), menuCase[1] + " must define the dynamic fallback item");
            assertTrue(definition.contains("  " + menuCase[2] + ":"), menuCase[1] + " must define typed dynamic item material " + menuCase[2]);
            assertTrue(definition.contains("material: " + menuCase[3]), menuCase[1] + " material must live in config-v2");
            assertTrue(menu.contains("GuiMenuRenderer.material(MENU"), menuCase[0] + " must read dynamic materials from the menu definition");
            assertFalse(menu.contains("switch ("), menuCase[0] + " must not switch on roles or upgrade types for materials");
            assertFalse(menu.contains("Material." + menuCase[3]), menuCase[0] + " must not hard-code typed item materials");
        }
    }

    @Test
    void statefulItemMaterialsRenderFromMenuDefinitions() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandBiomeMenu", "biome.yml", "_", "SELECTED", "LIME_DYE"},
                new String[] {"IslandFlagMenu", "flags.yml", "_", "TRUE", "LIME_DYE"},
                new String[] {"IslandMissionMenu", "missions.yml", "_", "COMPLETED", "LIME_DYE"},
                new String[] {"IslandWarpMenu", "warps.yml", "PRIVATE", "PUBLIC", "ENDER_EYE"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("  " + menuCase[2] + ":"), menuCase[1] + " must define the state fallback item");
            assertTrue(definition.contains("  " + menuCase[3] + ":"), menuCase[1] + " must define state material " + menuCase[3]);
            assertTrue(definition.contains("material: " + menuCase[4]), menuCase[1] + " state material must live in config-v2");
            assertTrue(menu.contains("GuiMenuRenderer.material(MENU") || menu.contains("GuiMenuRenderer.material(menu"), menuCase[0] + " must read state materials from the menu definition");
            assertFalse(menu.contains("Material." + menuCase[4]), menuCase[0] + " must not hard-code state item material");
        }
    }

    @Test
    void biomeListItemSlotsRenderFromMenuDefinitionLayout() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandBiomeMenu.java"));
        String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/biome.yml"));

        assertTrue(definition.contains("\"____C____\""), "biome menu must expose dynamic item slots in config-v2 layout");
        assertTrue(definition.contains("\"_________\""), "biome menu must expose enough YAML slots for biome entries");
        assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, \"_\")"), "biome entries must use menu definition slots");
        assertFalse(menu.contains("int slot = 9"), "biome entries must not start from a Java hard-coded slot");
        assertFalse(menu.contains("slot++"), "biome entries must not advance through Java hard-coded slots");
    }

    @Test
    void homeListItemSlotsRenderFromMenuDefinitionLayout() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandHomeMenu.java"));
        String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/homes.yml"));

        assertTrue(definition.contains("\"_________\""), "home menu must expose dynamic item slots in config-v2 layout");
        assertTrue(definition.contains("\"____E____\""), "home menu must keep the empty placeholder in config-v2 layout");
        assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, \"_\")"), "home entries must use menu definition slots");
        assertFalse(menu.contains("int slot = 0"), "home entries must not start from a Java hard-coded slot");
        assertFalse(menu.contains("slot++"), "home entries must not advance through Java hard-coded slots");
    }

    @Test
    void inviteAndRoleListItemSlotsRenderFromMenuDefinitionLayout() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandInviteMenu", "invites.yml"},
                new String[] {"IslandRoleMenu", "roles.yml"},
                new String[] {"IslandSnapshotMenu", "snapshots.yml"},
                new String[] {"IslandLimitMenu", "limits.yml"},
                new String[] {"IslandUpgradeMenu", "upgrades.yml"},
                new String[] {"IslandVisitMenu", "visit.yml"},
                new String[] {"IslandMyIslandsMenu", "my-islands.yml"},
                new String[] {"IslandBanMenu", "bans.yml"},
                new String[] {"IslandLogMenu", "logs.yml"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("\"_________\""), menuCase[1] + " must expose dynamic item slots in config-v2 layout");
            assertTrue(definition.contains("\"____E____\""), menuCase[1] + " must keep the empty placeholder in config-v2 layout");
            assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, \"_\")"), menuCase[0] + " entries must use menu definition slots");
            assertFalse(menu.contains("int slot = 0"), menuCase[0] + " entries must not start from a Java hard-coded slot");
            assertFalse(menu.contains("slot++"), menuCase[0] + " entries must not advance through Java hard-coded slots");
        }
    }

    @Test
    void stateAndPagedListSlotsRenderFromMenuDefinitionLayout() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandFlagMenu", "flags.yml", "GuiMenuRenderer.slots(MENU, \"_\")"},
                new String[] {"IslandMissionMenu", "missions.yml", "GuiMenuRenderer.slots(MENU, \"_\")"},
                new String[] {"IslandMemberMenu", "members.yml", "GuiMenuRenderer.slots(MENU, \"_\")"},
                new String[] {"IslandWarpMenu", "warps.yml", "GuiMenuRenderer.slots(menu, \"_\")"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("\"_________\""), menuCase[1] + " must expose dynamic item slots in config-v2 layout");
            assertTrue(menu.contains(menuCase[2]), menuCase[0] + " entries must use menu definition slots");
            assertFalse(menu.contains("int slot = 0"), menuCase[0] + " entries must not start from a Java hard-coded slot");
            assertFalse(menu.contains("slot++"), menuCase[0] + " entries must not advance through Java hard-coded slots");
        }
        String publicWarps = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/public-warps.yml"));
        assertTrue(publicWarps.contains("\"_________\""), "public-warps.yml must expose dynamic item slots in config-v2 layout");
    }

    @Test
    void rankingGroupSlotsRenderFromMenuDefinitionLayout() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandRankingMenu.java"));
        String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/ranking.yml"));

        assertTrue(definition.contains("\"LLLLLLLLL\""), "ranking level slots must live in config-v2 layout");
        assertTrue(definition.contains("\"WWWWWWWWW\""), "ranking worth slots must live in config-v2 layout");
        assertTrue(definition.contains("\"CCCCCCCCC\""), "ranking review slots must live in config-v2 layout");
        assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, symbol)"), "ranking entries must use menu definition slots");
        assertFalse(menu.contains("int slot = 9"), "ranking entries must not start from a Java hard-coded slot");
        assertFalse(menu.contains("slot++"), "ranking entries must not advance through Java hard-coded slots");
    }

    @Test
    void adminNodeActionSlotsRenderFromMenuDefinitionLayout() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminNodeMenu.java"));
        String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-node.yml"));

        assertTrue(definition.contains("\".LIADUSWM\""), "admin node action row must live in config-v2 layout");
        assertTrue(definition.contains("\"KQ..H.T.C\""), "admin node danger/help row must live in config-v2 layout");
        assertTrue(menu.contains("GuiMenuRenderer.slots(MENU, symbol)"), "admin node actions must use menu definition slots");
        assertFalse(menu.contains("slot <= 19"), "admin node actions must not use a Java hard-coded slot range");
    }

    @Test
    void remainingDynamicItemMaterialsRenderFromMenuDefinitions() throws Exception {
        for (String[] menuCase : List.of(
                new String[] {"IslandPermissionMenu", "permissions.yml", "ALLOW", "LIME_DYE"},
                new String[] {"IslandRankingMenu", "ranking.yml", "WORTH", "EMERALD"},
                new String[] {"IslandCreateMenu", "create.yml", "_", "OAK_SAPLING"},
                new String[] {"IslandLogMenu", "logs.yml", "BANK", "GOLD_INGOT"}
        )) {
            String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/" + menuCase[0] + ".java"));
            String definition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/" + menuCase[1]));

            assertTrue(definition.contains("  " + menuCase[2] + ":"), menuCase[1] + " must define dynamic material " + menuCase[2]);
            assertTrue(definition.contains("material: " + menuCase[3]), menuCase[1] + " material must live in config-v2");
            assertTrue(menu.contains("GuiMenuRenderer.material(MENU"), menuCase[0] + " must read materials from the menu definition");
            assertFalse(menu.contains("Material." + menuCase[3]), menuCase[0] + " must not hard-code dynamic item material");
        }
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
