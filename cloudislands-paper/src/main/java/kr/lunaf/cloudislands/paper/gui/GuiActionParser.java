package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
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
            if (safeAction.equals("island.member.promote.prepare") || safeAction.equals("island.member.demote.prepare") || memberRoleConfirmation(safeAction)) {
                return Optional.of(new GuiAction.MemberRoleChange(
                    safeAction,
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData
                ));
            }
            if (safeAction.equals("island.member.remove.prepare") || memberRemovalConfirmation(safeAction)) {
                return Optional.of(new GuiAction.MemberRemoval(
                    safeAction,
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData
                ));
            }
            if (safeAction.equals("island.ban.pardon.prepare") || banPardonConfirmation(safeAction)) {
                return Optional.of(new GuiAction.BanPardon(
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
            if (safeAction.equals("island.warp.delete.prepare") || warpDeleteConfirmation(safeAction)) {
                return Optional.of(new GuiAction.WarpDelete(
                    safeAction,
                    required(safeData, "warpName"),
                    safeData
                ));
            }
            if (safeAction.equals(DangerousGuiActionPolicy.RESET_CONFIRM_ACTION)) {
                return Optional.of(new GuiAction.DangerResetConfirm(
                    required(safeData, DangerousGuiActionPolicy.OPERATION_KEY),
                    required(safeData, DangerousGuiActionPolicy.TOKEN_KEY),
                    safeData.getOrDefault("reason", "player-reset")
                ));
            }
            if (safeAction.equals(DangerousGuiActionPolicy.DELETE_CONFIRM_ACTION)) {
                return Optional.of(new GuiAction.DangerDeleteConfirm(
                    required(safeData, DangerousGuiActionPolicy.OPERATION_KEY),
                    required(safeData, DangerousGuiActionPolicy.TOKEN_KEY)
                ));
            }
            return switch (safeAction) {
                case "island.create" -> Optional.of(new GuiAction.IslandCreate(
                    safeData.getOrDefault("templateId", "default")
                ));
                case "island.bank.deposit", "island.bank.withdraw" -> Optional.of(new GuiAction.BankAmount(
                    safeAction,
                    positiveDecimal(required(safeData, "amount"))
                ));
                case "island.snapshot.create" -> Optional.of(new GuiAction.SnapshotCreate(
                    safeData.getOrDefault("reason", "manual")
                ));
                case "island.biome.set" -> Optional.of(new GuiAction.BiomeSet(
                    required(safeData, "biomeKey")
                ));
                case "island.flag.set" -> Optional.of(new GuiAction.FlagSet(
                    enumValue(IslandFlag.class, required(safeData, "flag"))
                ));
                case "island.limit.set" -> Optional.of(new GuiAction.LimitSet(
                    required(safeData, "limitKey"),
                    nonNegativeLong(required(safeData, "value"))
                ));
                case "island.visit.target" -> Optional.of(new GuiAction.VisitTarget(
                    required(safeData, "target")
                ));
                case "island.home" -> Optional.of(new GuiAction.HomeTeleport(
                    required(safeData, "homeName")
                ));
                case "island.home.set" -> Optional.of(new GuiAction.HomeSet(
                    required(safeData, "homeName")
                ));
                case "island.warp.teleport" -> Optional.of(new GuiAction.WarpTeleport(
                    required(safeData, "warpName"),
                    optionalUuid(safeData, "islandId")
                ));
                case "island.warp.public", "island.warp.private" -> Optional.of(new GuiAction.WarpAccess(
                    safeAction,
                    required(safeData, "warpName"),
                    false
                ));
                case "island.warp.public.toggle" -> Optional.of(new GuiAction.WarpAccess(
                    safeAction,
                    required(safeData, "warpName"),
                    bool(required(safeData, "publicAccess"))
                ));
                case "island.invite.accept", "island.invite.decline" -> Optional.of(new GuiAction.InviteAction(
                    safeAction,
                    UUID.fromString(required(safeData, "inviteId"))
                ));
                case "island.members.page" -> Optional.of(new GuiAction.MemberPage(
                    integer(safeData.get("page"))
                ));
                case "island.member.detail" -> Optional.of(new GuiAction.MemberDetail(
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData.getOrDefault("playerName", ""),
                    safeData.getOrDefault("role", "unknown"),
                    safeData.getOrDefault("presenceState", "UNKNOWN"),
                    safeData.getOrDefault("lastSeenAt", "")
                ));
                case "island.permissions.page" -> Optional.of(new GuiAction.PermissionPage(
                    integer(safeData.get("page")),
                    integer(safeData.get("rolePage"))
                ));
                case "island.permissions.set" -> Optional.of(new GuiAction.ChangePermission(
                    new RoleId(required(safeData, "role")),
                    enumValue(IslandPermission.class, required(safeData, "permission")),
                    safeData.getOrDefault("expectedVersion", "")
                ));
                case "island.role.weight.adjust" -> Optional.of(new GuiAction.RoleWeightAdjust(
                    new RoleId(required(safeData, "role")),
                    nonNegativeInteger(required(safeData, "weight")),
                    safeData.getOrDefault("displayName", "")
                ));
                case "island.log.detail" -> Optional.of(new GuiAction.LogDetail(
                    required(safeData, "action"),
                    safeData.getOrDefault("actorUuid", ""),
                    safeData.getOrDefault("createdAt", ""),
                    safeData.getOrDefault("payload", "")
                ));
                case "island.mission.complete" -> Optional.of(new GuiAction.MissionComplete(
                    required(safeData, "missionKey"),
                    safeData.getOrDefault("kind", "MISSION"),
                    safeData.getOrDefault("label", "섬 미션")
                ));
                case "island.upgrade.purchase" -> Optional.of(new GuiAction.UpgradePurchase(
                    required(safeData, "upgradeKey")
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

    private static boolean memberRoleConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId)
            && (actionId.equals("island.member.promote") || actionId.equals("island.member.demote"));
    }

    private static boolean banPardonConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".ban.pardon.confirm");
    }

    private static boolean snapshotRestoreConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".snapshot.restore.confirm");
    }

    private static boolean warpDeleteConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".warp.delete.confirm");
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

    private static long nonNegativeLong(String value) {
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0L) {
            throw new IllegalArgumentException("non-negative value is required");
        }
        return parsed;
    }

    private static int nonNegativeInteger(String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("non-negative value is required");
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

    private static boolean bool(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("true")) {
            return true;
        }
        if (normalized.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("boolean value is required");
    }

    private static UUID optionalUuid(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
    }
}
