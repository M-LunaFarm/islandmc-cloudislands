package kr.lunaf.cloudislands.paper.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfirmationTokenPolicy {
    public static final String TOKEN_KEY = "confirmationToken";

    private static final Set<String> CONFIRMED_ACTIONS = Set.of(
        "island.warp.delete.confirm",
        "island.member.promote",
        "island.member.demote",
        "island.member.remove.confirm",
        "island.ban.pardon.confirm",
        "island.snapshot.restore.confirm",
        "admin.node.kickall.confirm",
        "admin.node.shutdown-safe.confirm"
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
