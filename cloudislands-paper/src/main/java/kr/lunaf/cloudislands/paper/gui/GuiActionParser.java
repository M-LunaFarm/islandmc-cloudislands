package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
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
            if (safeAction.equals("island.snapshot.restore.prepare") || snapshotRestoreConfirmation(safeAction)) {
                return Optional.of(new GuiAction.SnapshotRestore(
                    safeAction,
                    positiveLong(required(safeData, "snapshotNo")),
                    safeData
                ));
            }
            return switch (safeAction) {
                case "island.bank.deposit", "island.bank.withdraw" -> Optional.of(new GuiAction.BankAmount(
                    safeAction,
                    positiveDecimal(required(safeData, "amount"))
                ));
                case "island.snapshot.create" -> Optional.of(new GuiAction.SnapshotCreate(
                    safeData.getOrDefault("reason", "manual")
                ));
                case "island.permissions.page" -> Optional.of(new GuiAction.PermissionPage(
                    integer(safeData.get("page")),
                    integer(safeData.get("rolePage"))
                ));
                case "island.permissions.set" -> Optional.of(new GuiAction.ChangePermission(
                    new RoleId(required(safeData, "role")),
                    IslandPermission.valueOf(required(safeData, "permission").toUpperCase(java.util.Locale.ROOT).replace('-', '_')),
                    safeData.getOrDefault("expectedVersion", "")
                ));
                default -> GuiActionSchema.registered(safeAction)
                    ? Optional.of(new GuiAction.Raw(safeAction, safeData))
                    : Optional.empty();
            };
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean memberRemovalConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".member.remove.confirm");
    }

    private static boolean snapshotRestoreConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".snapshot.restore.confirm");
    }

    private static int integer(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(0, Integer.parseInt(value.trim()));
    }

    private static BigDecimal positiveDecimal(String value) {
        BigDecimal amount = new BigDecimal(value.trim());
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("positive amount is required");
        }
        return amount;
    }

    private static long positiveLong(String value) {
        long parsed = Long.parseLong(value.trim());
        if (parsed <= 0L) {
            throw new IllegalArgumentException("positive value is required");
        }
        return parsed;
    }

    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
