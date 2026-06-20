package kr.lunaf.cloudislands.paper.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum GuiClick {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    UNSUPPORTED;

    public static GuiClick from(InventoryClickEvent event) {
        return fromClickType(event == null ? null : event.getClick());
    }

    static GuiClick fromClickType(ClickType click) {
        if (click == null) {
            return UNSUPPORTED;
        }
        return switch (click) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case SHIFT_LEFT -> SHIFT_LEFT;
            case SHIFT_RIGHT -> SHIFT_RIGHT;
            default -> UNSUPPORTED;
        };
    }

    public boolean supported() {
        return this != UNSUPPORTED;
    }

    public boolean right() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }

    public boolean left() {
        return this == LEFT || this == SHIFT_LEFT;
    }

    public boolean shift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }
}
