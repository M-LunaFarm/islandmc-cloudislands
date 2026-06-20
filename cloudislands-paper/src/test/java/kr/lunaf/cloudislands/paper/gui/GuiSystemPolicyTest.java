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
        assertTrue(source.contains("private final GuiActionExecutor executor"), "GUI action executor must be constructor-injected");
        assertFalse(source.contains("AtomicReference"), "GUI action registry must not keep global mutable executor state");
        assertFalse(source.contains("static void configure"), "GUI action registry must not be reconfigured globally");
    }

    @Test
    void menuRegistrarInjectsGuiActionRegistry() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandGuiMenuRegistrar.java"));
        assertTrue(source.contains("new GuiActionRegistry(executor)"), "menu bootstrap must create the action registry instance");
        assertTrue(source.contains("GuiStateMenus.listener(registry)"), "state menus must share the injected action registry");
        assertFalse(source.contains("GuiActionRegistry.configure"), "menu bootstrap must not configure global registry state");
    }

    @Test
    void guiSessionsAreRevisionGuardedAndClearedOnPluginDisable() throws Exception {
        String sessions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiSessions.java"));
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));

        assertTrue(sessions.contains("AtomicLong REVISIONS"), "GUI sessions must carry a monotonically increasing revision");
        assertTrue(sessions.contains("CURRENT.put(player.getUniqueId(), session)"), "opening a GUI must replace the current player session");
        assertTrue(sessions.contains("session.equals(CURRENT.get(player.getUniqueId()))"), "delayed GUI responses must check the current player session");
        assertTrue(sessions.contains("runIfCurrent"), "async GUI rendering must be guarded by the current session");
        assertTrue(sessions.contains("CURRENT.clear()"), "GUI sessions must expose a lifecycle cleanup hook");
        assertTrue(plugin.contains("GuiSessions.clear()"), "plugin disable must clear stale GUI sessions");
    }
}
