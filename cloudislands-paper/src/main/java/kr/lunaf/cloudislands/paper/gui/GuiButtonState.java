package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;

public enum GuiButtonState {
    ENABLED("gui-button-state-enabled", "상태: 사용 가능", true),
    DISABLED_NO_PERMISSION("gui-button-state-disabled-no-permission", "상태: 권한 없음", false),
    DISABLED_REQUIREMENT_NOT_MET("gui-button-state-disabled-requirement-not-met", "상태: 조건 미충족", false),
    LOADING_OR_ERROR("gui-button-state-loading-or-error", "상태: 로딩 또는 오류", false);

    private final String messageKey;
    private final String fallback;
    private final boolean clickable;

    GuiButtonState(String messageKey, String fallback, boolean clickable) {
        this.messageKey = messageKey;
        this.fallback = fallback;
        this.clickable = clickable;
    }

    public boolean clickable() {
        return clickable;
    }

    public String lore(MessageRenderer messages) {
        return GuiMenuRenderer.message(messages, messageKey, fallback);
    }

    public List<String> dataLore(MessageRenderer messages) {
        return List.of(lore(messages), "buttonState=" + name());
    }
}
