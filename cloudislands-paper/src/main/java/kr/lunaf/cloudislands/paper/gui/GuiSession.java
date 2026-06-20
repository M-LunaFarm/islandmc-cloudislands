package kr.lunaf.cloudislands.paper.gui;

import java.util.UUID;

public record GuiSession(UUID sessionId, UUID playerId, String menuId, long revision) {
    public GuiSession {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is required");
        }
        menuId = menuId == null ? "" : menuId;
    }
}
