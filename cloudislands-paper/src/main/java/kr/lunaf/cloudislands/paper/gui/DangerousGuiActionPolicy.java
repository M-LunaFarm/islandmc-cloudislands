package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;

public final class DangerousGuiActionPolicy {
    public static final String OPERATION_KEY = "dangerOperation";
    public static final String TOKEN_KEY = "confirmationToken";
    public static final String RESET_OPERATION = "reset";
    public static final String DELETE_OPERATION = "delete";
    public static final String RESET_TOKEN = "RESET_WORLD";
    public static final String DELETE_TOKEN = "DELETE_ISLAND";

    private DangerousGuiActionPolicy() {
    }

    public static Map<String, String> resetConfirmationData() {
        return Map.of(OPERATION_KEY, RESET_OPERATION, TOKEN_KEY, RESET_TOKEN, "reason", "player-reset");
    }

    public static Map<String, String> deleteConfirmationData() {
        return Map.of(OPERATION_KEY, DELETE_OPERATION, TOKEN_KEY, DELETE_TOKEN);
    }

    public static boolean confirmed(Map<String, String> data, GuiClick click, String expectedOperation, String expectedToken) {
        if (click != GuiClick.LEFT || data == null) {
            return false;
        }
        return expectedOperation.equals(data.get(OPERATION_KEY)) && expectedToken.equals(data.get(TOKEN_KEY));
    }
}
