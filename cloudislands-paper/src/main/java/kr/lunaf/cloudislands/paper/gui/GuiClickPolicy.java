package kr.lunaf.cloudislands.paper.gui;

final class GuiClickPolicy {
    private GuiClickPolicy() {
    }

    static GuiClick fromClickName(String clickName) {
        if (clickName == null || clickName.isBlank()) {
            return GuiClick.UNSUPPORTED;
        }
        return switch (clickName.trim()) {
            case "LEFT" -> GuiClick.LEFT;
            case "RIGHT" -> GuiClick.RIGHT;
            case "SHIFT_LEFT" -> GuiClick.SHIFT_LEFT;
            case "SHIFT_RIGHT" -> GuiClick.SHIFT_RIGHT;
            default -> GuiClick.UNSUPPORTED;
        };
    }
}
