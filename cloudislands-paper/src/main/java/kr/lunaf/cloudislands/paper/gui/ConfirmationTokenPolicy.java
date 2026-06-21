package kr.lunaf.cloudislands.paper.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfirmationTokenPolicy {
    public static final String TOKEN_KEY = "confirmationToken";
    public static final String WARP_DELETE_CONFIRM_ACTION = "island.warp.delete.confirm";
    public static final String MEMBER_REMOVE_CONFIRM_ACTION = "island.member.remove.confirm";
    public static final String BAN_PARDON_CONFIRM_ACTION = "island.ban.pardon.confirm";
    public static final String SNAPSHOT_RESTORE_CONFIRM_ACTION = "island.snapshot.restore.confirm";
    public static final String ADMIN_NODE_KICKALL_CONFIRM_ACTION = "admin.node.kickall.confirm";
    public static final String ADMIN_NODE_SHUTDOWN_SAFE_CONFIRM_ACTION = "admin.node.shutdown-safe.confirm";

    private static final Set<String> CONFIRMED_ACTIONS = Set.of(
        WARP_DELETE_CONFIRM_ACTION,
        "island.member.promote",
        "island.member.demote",
        MEMBER_REMOVE_CONFIRM_ACTION,
        BAN_PARDON_CONFIRM_ACTION,
        SNAPSHOT_RESTORE_CONFIRM_ACTION,
        ADMIN_NODE_KICKALL_CONFIRM_ACTION,
        ADMIN_NODE_SHUTDOWN_SAFE_CONFIRM_ACTION
    );

    private ConfirmationTokenPolicy() {
    }

    public static boolean requiresToken(String actionId) {
        return CONFIRMED_ACTIONS.contains(actionId);
    }

    public static Map<String, String> withToken(String actionId, Map<String, String> data) {
        Map<String, String> values = new LinkedHashMap<>(data == null ? Map.of() : data);
        if (requiresToken(actionId)) {
            values.put(TOKEN_KEY, token(actionId));
        }
        return Map.copyOf(values);
    }

    public static boolean confirmed(String actionId, Map<String, String> data, GuiClick click) {
        if (!requiresToken(actionId)) {
            return true;
        }
        if (click != GuiClick.LEFT || data == null) {
            return false;
        }
        return token(actionId).equals(data.get(TOKEN_KEY));
    }

    public static String token(String actionId) {
        return "CONFIRM:" + actionId;
    }
}
