package kr.lunaf.cloudislands.paper.gui;

import java.util.Set;

public final class GuiActionSchema {
    private GuiActionSchema() {
    }

    public static Set<String> registeredActionIds() {
        return GuiActionParser.registeredActionIds();
    }

    public static boolean registered(String actionId) {
        return actionId != null && GuiActionParser.registeredActionIds().contains(actionId.trim());
    }
}
