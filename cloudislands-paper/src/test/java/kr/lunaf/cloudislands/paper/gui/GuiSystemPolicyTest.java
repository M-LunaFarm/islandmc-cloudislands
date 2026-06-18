package kr.lunaf.cloudislands.paper.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(List.of("MEMBER", "TRUSTED", "VISITOR"), GuiSystemPolicy.permissionMatrixRoles());
        assertEquals(List.of("BUILD", "BREAK", "CHEST", "DOOR", "PVP"), GuiSystemPolicy.permissionMatrixColumns());
        assertEquals(List.of("MEMBER=allow", "TRUSTED=allow", "VISITOR=deny"), GuiSystemPolicy.permissionMatrix().get("BUILD"));
        assertEquals(List.of("MEMBER=allow", "TRUSTED=deny", "VISITOR=deny"), GuiSystemPolicy.permissionMatrix().get("CHEST"));
        assertEquals(List.of("MEMBER=allow", "TRUSTED=allow", "VISITOR=allow"), GuiSystemPolicy.permissionMatrix().get("DOOR"));
        assertEquals(List.of("MEMBER=deny", "TRUSTED=deny", "VISITOR=deny"), GuiSystemPolicy.permissionMatrix().get("PVP"));
    }

    @Test
    void pinsNodeAdminDashboardFieldsAndActions() {
        assertEquals(List.of("node-id", "players", "mspt", "active-islands", "queue", "state"), GuiSystemPolicy.nodeAdminFields());
        for (String action : List.of("Drain", "Undrain", "View Islands", "Move Load", "Shutdown Safe")) {
            assertTrue(GuiSystemPolicy.nodeAdminAction(action), action);
        }
    }
}
