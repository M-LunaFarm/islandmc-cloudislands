package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;

public final class GuiSystemPolicy {
    public static final String MAIN_MENU_POLICY =
            "main-gui-links-core-player-flows-with-command-backed-buttons";
    public static final String MEMBER_MENU_POLICY =
            "member-gui-shows-membership-actions-online-state-and-last-seen-state";
    public static final String PERMISSION_MATRIX_POLICY =
            "permission-gui-renders-core-role-catalog-by-full-api-permission-matrix";
    public static final String NODE_ADMIN_POLICY =
            "node-admin-gui-shows-node-load-and-safe-operation-buttons";

    private static final List<String> MAIN_MENU_BUTTONS = List.of(
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
    );

    private static final List<String> MEMBER_MENU_FIELDS = List.of(
            "member-list",
            "invite",
            "kick",
            "promote",
            "demote",
            "transfer-ownership",
            "online-state",
            "last-seen-at"
    );

    private static final List<String> PERMISSION_MATRIX_ROLES = List.of(
            "CORE_ROLE_CATALOG",
            "VISITOR_FALLBACK"
    );

    private static final List<String> PERMISSION_MATRIX_COLUMNS = List.of(
            "IslandPermission.values()"
    );

    private static final Map<String, List<String>> PERMISSION_MATRIX = Map.of(
            "role-source", List.of("PaperGuiViews.islandRoles", "VISITOR_FALLBACK"),
            "permission-source", List.of("IslandPermission.values()"),
            "write-mode", List.of("stage", "save-batch")
    );

    private static final List<String> NODE_ADMIN_FIELDS = List.of(
            "node-id",
            "players",
            "mspt",
            "active-islands",
            "queue",
            "state"
    );

    private static final List<String> NODE_ADMIN_ACTIONS = List.of(
            "Drain",
            "Undrain",
            "View Islands",
            "Move Load",
            "Shutdown Safe"
    );

    private GuiSystemPolicy() {
    }

    public static List<String> mainMenuButtons() {
        return MAIN_MENU_BUTTONS;
    }

    public static List<String> memberMenuFields() {
        return MEMBER_MENU_FIELDS;
    }

    public static List<String> permissionMatrixRoles() {
        return PERMISSION_MATRIX_ROLES;
    }

    public static List<String> permissionMatrixColumns() {
        return PERMISSION_MATRIX_COLUMNS;
    }

    public static Map<String, List<String>> permissionMatrix() {
        return PERMISSION_MATRIX;
    }

    public static List<String> nodeAdminFields() {
        return NODE_ADMIN_FIELDS;
    }

    public static List<String> nodeAdminActions() {
        return NODE_ADMIN_ACTIONS;
    }

    public static boolean mainMenuButton(String key) {
        return MAIN_MENU_BUTTONS.contains(key);
    }

    public static boolean nodeAdminAction(String action) {
        return NODE_ADMIN_ACTIONS.contains(action);
    }
}
