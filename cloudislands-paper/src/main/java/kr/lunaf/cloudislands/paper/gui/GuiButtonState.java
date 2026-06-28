package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;

public enum GuiButtonState {
    ENABLED("gui-button-state-enabled", "상태: 사용 가능", true),
    DISABLED_NO_PERMISSION("gui-button-state-disabled-no-permission", "상태: 권한 없음", false),
    DISABLED_REQUIREMENT_NOT_MET("gui-button-state-disabled-requirement-not-met", "상태: 조건 미충족", false),
    DISABLED_NOT_ENOUGH_MONEY("gui-button-state-disabled-not-enough-money", "상태: 잔액 부족", false),
    LOADING("gui-button-state-loading", "상태: 처리 중", false),
    ERROR_RETRYABLE("gui-button-state-error-retryable", "상태: 재시도 가능 오류", false),
    ERROR_FATAL("gui-button-state-error-fatal", "상태: 처리 불가 오류", false);

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
