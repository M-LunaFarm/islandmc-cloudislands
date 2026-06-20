package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public final class GuiActionParser {
    private GuiActionParser() {
    }

    public static Optional<GuiAction> parse(String actionId, Map<String, String> data) {
        String safeAction = actionId == null ? "" : actionId.trim();
        Map<String, String> safeData = data == null ? Map.of() : data;
        if (safeAction.isBlank()) {
            return Optional.empty();
        }
        try {
            if (safeAction.equals("island.member.remove.prepare") || memberRemovalConfirmation(safeAction)) {
                return Optional.of(new GuiAction.MemberRemoval(
                    safeAction,
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData
                ));
            }
            return switch (safeAction) {
                case "island.permissions.page" -> Optional.of(new GuiAction.PermissionPage(
                    integer(safeData.get("page")),
                    integer(safeData.get("rolePage"))
                ));
                case "island.permissions.set" -> Optional.of(new GuiAction.ChangePermission(
                    new RoleId(required(safeData, "role")),
                    IslandPermission.valueOf(required(safeData, "permission").toUpperCase(java.util.Locale.ROOT).replace('-', '_')),
                    safeData.getOrDefault("expectedVersion", "")
                ));
                default -> Optional.of(new GuiAction.Raw(safeAction, safeData));
            };
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean memberRemovalConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".member.remove.confirm");
    }

    private static int integer(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(0, Integer.parseInt(value.trim()));
    }

    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
