package kr.lunaf.cloudislands.paper.gui;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public final class GuiActionParser {
    private static final Set<String> REGISTERED_ACTIONS = Set.of(
        "admin.island.migrate.prompt",
        "admin.island.where.prompt",
        "admin.node.drain",
        "admin.node.info",
        "admin.node.islands",
        "admin.node.kickall.confirm",
        "admin.node.kickall.prepare",
        "admin.node.list",
        "admin.node.open",
        "admin.node.shutdown-safe.confirm",
        "admin.node.shutdown-safe.prepare",
        "admin.node.sweep",
        "admin.node.undrain",
        "gui.close",
        "island.ban.pardon.confirm",
        "island.ban.pardon.prepare",
        "island.bank.deposit",
        "island.bank.open",
        "island.bank.withdraw",
        "island.bans.list",
        "island.bans.open",
        "island.border.open",
        "island.biome.open",
        "island.biome.set",
        "island.biome.show",
        "island.chat.open",
        "island.create",
        "island.create.open",
        "island.danger.delete.confirm",
        "island.danger.delete.prepare",
        "island.danger.open",
        "island.danger.reset.confirm",
        "island.danger.reset.prepare",
        "island.flag.set",
        "island.flags.list",
        "island.flags.open",
        "island.home",
        "island.home.set",
        "island.homes.open",
        "island.info.open",
        "island.invite.accept",
        "island.invite.decline",
        "island.invites.open",
        "island.level.recalculate",
        "island.level.show",
        "island.limit.set",
        "island.limits.list",
        "island.limits.open",
        "island.list.open",
        "island.lock.toggle",
        "island.log.detail",
        "island.logs.list",
        "island.logs.open",
        "island.main.open",
        "island.member.demote",
        "island.member.demote.prepare",
        "island.member.detail",
        "island.member.invite",
        "island.member.invite.help",
        "island.member.list",
        "island.member.promote",
        "island.member.promote.prepare",
        "island.member.remove.confirm",
        "island.member.remove.prepare",
        "island.member.role",
        "island.members.open",
        "island.members.page",
        "island.mission.complete",
        "island.missions.open",
        "island.permissions.list",
        "island.permissions.open",
        "island.permissions.page",
        "island.permissions.reset",
        "island.permissions.save",
        "island.permissions.set",
        "island.public.toggle",
        "island.ranking.list",
        "island.ranking.open",
        "island.reviews.open",
        "island.visitor-stats.open",
        "island.role.weight.adjust",
        "island.roles.list",
        "island.roles.open",
        "island.settings.open",
        "island.snapshot.create",
        "island.snapshot.restore.confirm",
        "island.snapshot.restore.prepare",
        "island.snapshots.list",
        "island.snapshots.open",
        "island.upgrade.purchase",
        "island.upgrades.list",
        "island.upgrades.open",
        "island.visit.open",
        "island.visit.public.open",
        "island.visit.random",
        "island.visit.target",
        "island.warehouse.open",
        "island.warp.delete.confirm",
        "island.warp.delete.prepare",
        "island.warp.private",
        "island.warp.public",
        "island.warp.public.toggle",
        "island.warp.teleport",
        "island.warps.open",
        "island.worth.show"
    );

    private GuiActionParser() {
    }

    public static Set<String> registeredActionIds() {
        return REGISTERED_ACTIONS;
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
                    memberRoleChangeType(safeAction),
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
                ));
            }
            if (safeAction.equals("island.member.remove.prepare") || memberRemovalConfirmation(safeAction)) {
                return Optional.of(new GuiAction.MemberRemoval(
                    safeAction.equals("island.member.remove.prepare") ? GuiAction.MemberRemovalType.PREPARE : GuiAction.MemberRemovalType.CONFIRM,
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
                ));
            }
            if (safeAction.equals("island.ban.pardon.prepare") || banPardonConfirmation(safeAction)) {
                return Optional.of(new GuiAction.BanPardon(
                    safeAction.equals("island.ban.pardon.prepare") ? GuiAction.BanPardonType.PREPARE : GuiAction.BanPardonType.CONFIRM,
                    UUID.fromString(required(safeData, "playerUuid")),
                    safeData.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
                ));
            }
            if (safeAction.equals("island.snapshot.restore.prepare") || snapshotRestoreConfirmation(safeAction)) {
                return Optional.of(new GuiAction.SnapshotRestore(
                    safeAction.equals("island.snapshot.restore.prepare") ? GuiAction.SnapshotRestoreType.PREPARE : GuiAction.SnapshotRestoreType.CONFIRM,
                    positiveLong(required(safeData, "snapshotNo")),
                    safeData.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
                ));
            }
            if (safeAction.equals("island.warp.delete.prepare") || warpDeleteConfirmation(safeAction)) {
                return Optional.of(new GuiAction.WarpDelete(
                    safeAction.equals("island.warp.delete.prepare") ? GuiAction.WarpDeleteType.PREPARE : GuiAction.WarpDeleteType.CONFIRM,
                    required(safeData, "warpName"),
                    safeData.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
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
                case "gui.close" -> Optional.of(new GuiAction.Close());
                case "admin.node.open" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.OPEN, safeData));
                case "admin.node.list" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.LIST, safeData));
                case "admin.node.info" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.INFO, safeData));
                case "admin.node.islands" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.ISLANDS, safeData));
                case "admin.node.drain" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.DRAIN, safeData));
                case "admin.node.undrain" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.UNDRAIN, safeData));
                case "admin.node.sweep" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.SWEEP, safeData));
                case "admin.node.kickall.prepare" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.KICKALL_PREPARE, safeData));
                case "admin.node.shutdown-safe.prepare" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.SHUTDOWN_SAFE_PREPARE, safeData));
                case "admin.node.kickall.confirm" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.KICKALL_CONFIRM, safeData));
                case "admin.node.shutdown-safe.confirm" -> Optional.of(adminNode(GuiAction.AdminNodeActionType.SHUTDOWN_SAFE_CONFIRM, safeData));
                case "admin.island.where.prompt" -> Optional.of(new GuiAction.AdminIslandPrompt(
                    GuiAction.AdminIslandPromptType.WHERE,
                    safeData.getOrDefault("nodeId", "")
                ));
                case "admin.island.migrate.prompt" -> Optional.of(new GuiAction.AdminIslandPrompt(
                    GuiAction.AdminIslandPromptType.MIGRATE,
                    safeData.getOrDefault("nodeId", "")
                ));
                case "island.bank.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BANK_OPEN));
                case "island.snapshots.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.SNAPSHOTS_OPEN));
                case "island.snapshots.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.SNAPSHOTS_LIST));
                case "island.ranking.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.RANKING_OPEN));
                case "island.reviews.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.REVIEWS_OPEN));
                case "island.visitor-stats.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.VISITOR_STATS_OPEN));
                case "island.level.recalculate" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.LEVEL_RECALCULATE));
                case "island.level.show" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.LEVEL_SHOW));
                case "island.worth.show" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.WORTH_SHOW));
                case "island.upgrades.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.UPGRADES_OPEN));
                case "island.upgrades.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.UPGRADES_LIST));
                case "island.homes.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.HOMES_OPEN));
                case "island.warps.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.WARPS_OPEN));
                case "island.warehouse.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.WAREHOUSE_OPEN));
                case "island.visit.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.VISIT_OPEN));
                case "island.visit.random" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.VISIT_RANDOM));
                case "island.visit.public.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.VISIT_PUBLIC_OPEN));
                case "island.create.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.CREATE_OPEN));
                case "island.danger.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.DANGER_OPEN));
                case "island.danger.reset.prepare" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.DANGER_RESET_PREPARE));
                case "island.danger.delete.prepare" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.DANGER_DELETE_PREPARE));
                case "island.members.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.MEMBERS_OPEN));
                case "island.member.role" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.MEMBER_ROLE));
                case "island.member.invite" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.MEMBER_INVITE));
                case "island.member.invite.help" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.MEMBER_INVITE_HELP));
                case "island.member.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.MEMBER_LIST));
                case "island.invites.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.INVITES_OPEN));
                case "island.bans.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BANS_OPEN));
                case "island.bans.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BANS_LIST));
                case "island.permissions.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_OPEN));
                case "island.permissions.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_LIST));
                case "island.permissions.save" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_SAVE));
                case "island.permissions.reset" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_RESET));
                case "island.roles.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.ROLES_OPEN));
                case "island.roles.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.ROLES_LIST));
                case "island.main.open" -> Optional.of(new GuiAction.MainOpen());
                case "island.info.open" -> Optional.of(new GuiAction.InfoOpen());
                case "island.list.open" -> Optional.of(new GuiAction.IslandListOpen());
                case "island.chat.open" -> Optional.of(new GuiAction.ChatOpen());
                case "island.logs.open" -> Optional.of(new GuiAction.LogsOpen());
                case "island.logs.list" -> Optional.of(new GuiAction.LogsList());
                case "island.biome.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BIOME_OPEN));
                case "island.border.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BORDER_OPEN));
                case "island.biome.show" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.BIOME_SHOW));
                case "island.limits.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.LIMITS_OPEN));
                case "island.limits.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.LIMITS_LIST));
                case "island.settings.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.SETTINGS_OPEN));
                case "island.public.toggle" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.PUBLIC_TOGGLE));
                case "island.lock.toggle" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.LOCK_TOGGLE));
                case "island.flags.open" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.FLAGS_OPEN));
                case "island.flags.list" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.FLAGS_LIST));
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
                case "island.ranking.list" -> Optional.of(new GuiAction.RankingList(
                    safeData.getOrDefault("kind", "")
                ));
                case "island.missions.open" -> Optional.of(new GuiAction.MissionsOpen(
                    safeData.getOrDefault("kind", "MISSION")
                ));
                case "island.mission.complete" -> Optional.of(new GuiAction.MissionComplete(
                    required(safeData, "missionKey"),
                    safeData.getOrDefault("kind", "MISSION"),
                    safeData.getOrDefault("label", "섬 미션")
                ));
                case "island.upgrade.purchase" -> Optional.of(new GuiAction.UpgradePurchase(
                    required(safeData, "upgradeKey")
                ));
                default -> Optional.empty();
            };
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean memberRemovalConfirmation(String actionId) {
        return ConfirmationTokenPolicy.requiresToken(actionId) && actionId.endsWith(".member.remove.confirm");
    }

    private static GuiAction.MemberRoleChangeType memberRoleChangeType(String actionId) {
        return switch (actionId) {
            case "island.member.promote.prepare" -> GuiAction.MemberRoleChangeType.PROMOTE_PREPARE;
            case "island.member.demote.prepare" -> GuiAction.MemberRoleChangeType.DEMOTE_PREPARE;
            case "island.member.promote" -> GuiAction.MemberRoleChangeType.PROMOTE_CONFIRM;
            case "island.member.demote" -> GuiAction.MemberRoleChangeType.DEMOTE_CONFIRM;
            default -> throw new IllegalArgumentException("unsupported member role action");
        };
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

    private static GuiAction.AdminNodeAction adminNode(GuiAction.AdminNodeActionType type, Map<String, String> data) {
        return new GuiAction.AdminNodeAction(
            type,
            data.getOrDefault("nodeId", ""),
            data.getOrDefault("reason", "admin-gui"),
            data.getOrDefault(ConfirmationTokenPolicy.TOKEN_KEY, "")
        );
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
