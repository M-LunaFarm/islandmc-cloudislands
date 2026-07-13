package kr.lunaf.cloudislands.paper.command;

import java.util.Locale;
import java.util.UUID;

final class CurrentIslandVisitorPolicy {
    private CurrentIslandVisitorPolicy() {
    }

    static boolean visitor(UUID islandId, UUID playerIslandId, String memberRoleKey) {
        if (islandId == null || !islandId.equals(playerIslandId)) {
            return false;
        }
        String role = memberRoleKey == null ? "" : memberRoleKey.trim().toUpperCase(Locale.ROOT);
        return role.isBlank() || role.equals("TRUSTED");
    }
}
