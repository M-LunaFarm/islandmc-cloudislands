package kr.lunaf.cloudislands.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandRole;

public final class CloudEventMapper {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private CloudEventMapper() {}

    public static Optional<CloudEvent> map(GlobalEventSnapshot snapshot) {
        if (snapshot == null || snapshot.type() == null || snapshot.type().isBlank()) {
            return Optional.empty();
        }
        Map<String, String> fields = snapshot.fields() == null ? Map.of() : snapshot.fields();
        Instant occurredAt = snapshot.occurredAt();
        return switch (snapshot.type()) {
            case "ISLAND_PRE_CREATE", "ISLAND_CREATE_REQUESTED" -> Optional.of(new IslandPreCreateEvent(uuid(fields, "islandId"), firstUuid(fields, "ownerUuid", "playerUuid"), firstText(fields, "templateId", "template"), occurredAt));
            case "ISLAND_CREATED" -> Optional.of(new IslandCreatedEvent(uuid(fields, "islandId"), uuid(fields, "ownerUuid"), occurredAt));
            case "ISLAND_DELETED" -> Optional.of(new IslandDeletedEvent(uuid(fields, "islandId"), longValue(fields, "snapshotNo"), occurredAt));
            case "ISLAND_DELETE_REQUESTED" -> Optional.of(new IslandDeleteRequestEvent(uuid(fields, "islandId"), firstText(fields, "targetNode", "nodeId"), text(fields, "reason"), occurredAt));
            case "ISLAND_DELETE_BACKUP_FAILED" -> Optional.of(new IslandDeleteBackupFailedEvent(uuid(fields, "islandId"), text(fields, "reason"), text(fields, "error"), occurredAt));
            case "ISLAND_PRE_ACTIVATE" -> Optional.of(new IslandPreActivateEvent(uuid(fields, "islandId"), firstText(fields, "targetNode", "nodeId"), occurredAt));
            case "ISLAND_ACTIVATE_REQUESTED" -> Optional.of(new IslandActivationRequestEvent(uuid(fields, "islandId"), text(fields, "state"), firstText(fields, "targetNode", "nodeId"), occurredAt));
            case "ISLAND_ACTIVATED" -> Optional.of(new IslandActivatedEvent(uuid(fields, "islandId"), text(fields, "nodeId"), text(fields, "worldName"), intValue(fields, "cellX"), intValue(fields, "cellZ"), text(fields, "placementSource"), occurredAt));
            case "ISLAND_DEACTIVATE_REQUESTED" -> Optional.of(new IslandDeactivationRequestEvent(uuid(fields, "islandId"), text(fields, "state"), occurredAt));
            case "ISLAND_DEACTIVATED" -> Optional.of(new IslandDeactivatedEvent(uuid(fields, "islandId"), firstText(fields, "nodeId", "sourceNode"), text(fields, "targetNode"), text(fields, "phase"), longValue(fields, "snapshotNo"), occurredAt));
            case "ISLAND_MIGRATE_REQUESTED" -> Optional.of(new IslandMigrationEvent(uuid(fields, "islandId"), true, firstText(fields, "sourceNode", "fromNode"), firstText(fields, "targetNode", "toNode"), text(fields, "phase"), text(fields, "worldName"), intValue(fields, "cellX"), intValue(fields, "cellZ"), longValue(fields, "fencingToken"), occurredAt));
            case "ISLAND_MIGRATED" -> Optional.of(new IslandMigratedEvent(uuid(fields, "islandId"), text(fields, "fromNode"), firstText(fields, "toNode", "targetNode"), text(fields, "worldName"), intValue(fields, "cellX"), intValue(fields, "cellZ"), longValue(fields, "fencingToken"), text(fields, "placementSource"), occurredAt));
            case "ISLAND_RESTORE_REQUESTED" -> Optional.of(new IslandRestoreRequestEvent(uuid(fields, "islandId"), text(fields, "state"), firstText(fields, "targetNode", "nodeId"), longValue(fields, "snapshotNo"), longValue(fields, "fencingToken"), occurredAt));
            case "ISLAND_RESTORED" -> Optional.of(new IslandRestoredEvent(uuid(fields, "islandId"), longValue(fields, "snapshotNo"), text(fields, "state"), occurredAt));
            case "ISLAND_RESET_REQUESTED" -> Optional.of(new IslandResetEvent(uuid(fields, "islandId"), true, text(fields, "state"), firstText(fields, "targetNode", "nodeId"), text(fields, "reason"), occurredAt));
            case "ISLAND_RESET" -> Optional.of(new IslandResetEvent(uuid(fields, "islandId"), false, text(fields, "state"), firstText(fields, "targetNode", "nodeId"), text(fields, "reason"), occurredAt));
            case "ISLAND_RECOVERY_REQUIRED" -> Optional.of(new IslandRecoveryRequiredEvent(uuid(fields, "islandId"), text(fields, "nodeId"), text(fields, "reason"), occurredAt));
            case "ISLAND_REPAIRED" -> Optional.of(new IslandRepairedEvent(uuid(fields, "islandId"), text(fields, "reason"), occurredAt));
            case "ISLAND_RUNTIME_CHANGED" -> Optional.of(new IslandRuntimeChangeEvent(uuid(fields, "islandId"), text(fields, "state"), firstText(fields, "targetNode", "nodeId"), text(fields, "reason"), text(fields, "error"), occurredAt));
            case "ISLAND_PRE_VISIT", "ISLAND_VISIT_REQUESTED" -> Optional.of(new IslandPreVisitEvent(uuid(fields, "islandId"), firstUuid(fields, "visitorUuid", "playerUuid"), occurredAt));
            case "ISLAND_VISITED" -> Optional.of(new IslandVisitEvent(uuid(fields, "islandId"), firstUuid(fields, "visitorUuid", "playerUuid"), firstText(fields, "nodeId", "targetNode"), occurredAt));
            case "ISLAND_INVITE_CHANGED" -> Optional.of(new IslandInviteChangeEvent(uuid(fields, "islandId"), firstUuid(fields, "inviteId", "id"), firstUuid(fields, "playerUuid", "actorUuid"), firstUuid(fields, "targetUuid", "targetPlayerUuid"), text(fields, "state"), nullableBool(fields, "accepted"), nullableBool(fields, "declined"), occurredAt));
            case "ISLAND_MEMBER_JOINED" -> Optional.of(new IslandMemberJoinEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), firstRole(fields, "role", "newRole"), occurredAt));
            case "ISLAND_MEMBER_LEFT" -> Optional.of(new IslandMemberLeaveEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), occurredAt));
            case "ISLAND_MEMBER_CHANGED" -> Optional.of(new IslandMemberChangedEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), text(fields, "action"), role(fields, "oldRole"), firstRole(fields, "newRole", "role"), occurredAt));
            case "ISLAND_MEMBER_ROLE_CHANGED" -> Optional.of(new IslandRoleChangeEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), role(fields, "oldRole"), firstRole(fields, "newRole", "role"), occurredAt));
            case "ISLAND_OWNERSHIP_CHANGED" -> Optional.of(new IslandOwnershipChangeEvent(uuid(fields, "islandId"), firstUuid(fields, "actorUuid", "playerUuid"), firstUuid(fields, "targetUuid", "newOwnerUuid"), occurredAt));
            case "ISLAND_RENAMED" -> Optional.of(new IslandRenamedEvent(uuid(fields, "islandId"), firstUuid(fields, "actorUuid", "playerUuid"), firstText(fields, "name", "islandName"), occurredAt));
            case "ISLAND_ACCESS_CHANGED" -> Optional.of(new IslandAccessChangeEvent(uuid(fields, "islandId"), nullableBool(fields, "publicAccess"), nullableBool(fields, "locked"), occurredAt));
            case "ISLAND_VISITOR_BAN_CHANGED" -> Optional.of(new IslandVisitorBanChangeEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), bool(fields, "banned"), occurredAt));
            case "ISLAND_VISITOR_KICKED" -> Optional.of(new IslandVisitorKickEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), firstUuid(fields, "actorUuid", "requesterUuid"), occurredAt));
            case "ISLAND_FLAG_CHANGED" -> Optional.of(new IslandFlagChangeEvent(uuid(fields, "islandId"), flag(fields, "flag"), text(fields, "value"), occurredAt));
            case "ISLAND_PERMISSION_CHECKED" -> Optional.of(new IslandPermissionCheckEvent(uuid(fields, "islandId"), firstUuid(fields, "playerUuid", "targetUuid"), permission(fields, "permission"), bool(fields, "allowed"), occurredAt));
            case "ISLAND_PERMISSION_CHANGED" -> Optional.of(new IslandPermissionChangeEvent(uuid(fields, "islandId"), firstRole(fields, "role", "targetRole"), permission(fields, "permission"), nullableBool(fields, "allowed"), occurredAt));
            case "ISLAND_ROLE_CHANGED" -> Optional.of(new IslandRoleCatalogChangeEvent(uuid(fields, "islandId"), firstRole(fields, "role", "targetRole"), text(fields, "operation"), occurredAt));
            case "ISLAND_BIOME_CHANGED" -> Optional.of(new IslandBiomeChangeEvent(uuid(fields, "islandId"), firstText(fields, "biomeKey", "biome"), occurredAt));
            case "ISLAND_HOME_CHANGED" -> Optional.of(new IslandHomeChangeEvent(uuid(fields, "islandId"), firstText(fields, "homeName", "name"), occurredAt));
            case "ISLAND_WARP_CREATED" -> Optional.of(new IslandWarpCreateEvent(uuid(fields, "islandId"), firstText(fields, "warpName", "name"), location(fields), occurredAt));
            case "ISLAND_WARP_DELETED" -> Optional.of(new IslandWarpDeleteEvent(uuid(fields, "islandId"), firstText(fields, "warpName", "name"), occurredAt));
            case "ISLAND_WARP_CHANGED" -> Optional.of(new IslandWarpChangeEvent(uuid(fields, "islandId"), firstText(fields, "warpName", "name"), text(fields, "operation"), occurredAt));
            case "ISLAND_BANK_CHANGED" -> Optional.of(new IslandBankChangeEvent(uuid(fields, "islandId"), text(fields, "operation"), text(fields, "amount"), text(fields, "balance"), occurredAt));
            case "ISLAND_CHAT_SENT" -> Optional.of(new IslandChatSentEvent(uuid(fields, "islandId"), uuid(fields, "playerUuid"), text(fields, "channel"), text(fields, "message"), occurredAt));
            case "ISLAND_MISSION_PROGRESS" -> Optional.of(new IslandMissionProgressEvent(uuid(fields, "islandId"), text(fields, "missionKey"), text(fields, "kind"), longValue(fields, "progress"), longValue(fields, "goal"), longValue(fields, "amount"), bool(fields, "completed"), occurredAt));
            case "ISLAND_MISSION_COMPLETED" -> Optional.of(new IslandMissionCompleteEvent(uuid(fields, "islandId"), text(fields, "missionKey"), text(fields, "kind"), occurredAt));
            case "ISLAND_LEVEL_UPDATED" -> Optional.of(new IslandLevelRecalculateEvent(uuid(fields, "islandId"), longValue(fields, "level"), decimalOrNull(fields, "worth"), occurredAt));
            case "ISLAND_WORTH_CHANGED" -> Optional.of(new IslandWorthChangeEvent(uuid(fields, "islandId"), decimal(fields, "worth"), occurredAt));
            case "ISLAND_UPGRADE" -> Optional.of(new IslandUpgradeEvent(uuid(fields, "islandId"), text(fields, "upgradeKey"), intValue(fields, "level"), occurredAt));
            case "ISLAND_LIMIT_CHANGED" -> Optional.of(new IslandLimitChangeEvent(uuid(fields, "islandId"), firstText(fields, "limitKey", "key"), longValue(fields, "value"), occurredAt));
            case "ISLAND_BLOCKS_CHANGED" -> Optional.of(new IslandBlocksChangeEvent(uuid(fields, "islandId"), text(fields, "materialKey"), text(fields, "delta"), occurredAt));
            case "ISLAND_BLOCK_VALUE_CHANGED" -> Optional.of(new IslandBlockValueChangeEvent(text(fields, "materialKey"), decimal(fields, "worth"), longValue(fields, "levelPoints"), longValue(fields, "limit"), occurredAt));
            case "ISLAND_SNAPSHOT_REQUESTED" -> Optional.of(new IslandSnapshotRequestEvent(uuid(fields, "islandId"), text(fields, "reason"), occurredAt));
            case "ISLAND_SNAPSHOT_CREATED" -> Optional.of(new IslandSnapshotCreateEvent(uuid(fields, "islandId"), longValue(fields, "snapshotNo"), text(fields, "reason"), occurredAt));
            case "NODE_STATE_CHANGED" -> Optional.of(new NodeStateChangedEvent(text(fields, "nodeId"), text(fields, "state"), text(fields, "operation"), text(fields, "reason"), intValue(fields, "recoveryRequired"), occurredAt));
            case "ROUTE_TICKET_CREATED" -> Optional.of(new RouteTicketCreatedEvent(uuid(fields, "ticketId"), uuid(fields, "islandId"), uuid(fields, "playerUuid"), text(fields, "action"), text(fields, "targetNode"), text(fields, "targetServerName"), text(fields, "state"), occurredAt));
            case "ROUTE_SESSION_PUBLISHED" -> Optional.of(new RouteSessionPublishedEvent(uuid(fields, "ticketId"), uuid(fields, "islandId"), uuid(fields, "playerUuid"), text(fields, "action"), text(fields, "targetNode"), text(fields, "targetServerName"), occurredAt));
            case "ROUTE_TICKET_CONSUMED" -> Optional.of(new RouteTicketConsumedGlobalEvent(uuid(fields, "ticketId"), uuid(fields, "islandId"), uuid(fields, "playerUuid"), text(fields, "action"), text(fields, "targetNode"), text(fields, "targetServerName"), occurredAt));
            case "ROUTE_TICKET_FAILED" -> Optional.of(new RouteTicketFailedEvent(uuid(fields, "ticketId"), uuid(fields, "islandId"), uuid(fields, "playerUuid"), text(fields, "action"), text(fields, "targetNode"), text(fields, "targetServerName"), text(fields, "requestedNode"), text(fields, "reason"), occurredAt));
            case "ROUTE_TICKET_CLEARED" -> Optional.of(new RouteTicketClearedEvent(uuid(fields, "ticketId"), uuid(fields, "playerUuid"), text(fields, "reason"), bool(fields, "clearedSession"), bool(fields, "clearedTicket"), occurredAt));
            case "ISLAND_TEMPLATE_CHANGED" -> Optional.of(new IslandTemplateChangeEvent(text(fields, "templateId"), nullableBool(fields, "enabled"), text(fields, "operation"), text(fields, "minNodeVersion"), occurredAt));
            case "ADDON_STATE_CHANGED" -> Optional.of(new AddonStateChangeEvent(text(fields, "addonId"), uuid(fields, "islandId"), text(fields, "operation"), text(fields, "key"), text(fields, "table"), intValue(fields, "keys"), intValue(fields, "valueKeys"), intValue(fields, "tableKeys"), intValue(fields, "tables"), occurredAt));
            case "CORE_CACHE_CLEARED" -> Optional.of(new CoreCacheClearEvent(text(fields, "scope"), intValue(fields, "sessions"), intValue(fields, "tickets"), intValue(fields, "redisKeys"), occurredAt));
            case "CORE_RELOADED" -> Optional.of(new CoreReloadEvent(intValue(fields, "clearedSessions"), intValue(fields, "clearedTickets"), intValue(fields, "clearedRedisKeys"), occurredAt));
            default -> Optional.empty();
        };
    }

    private static String text(Map<String, String> fields, String key) {
        return fields.getOrDefault(key, "");
    }

    private static String firstText(Map<String, String> fields, String first, String second) {
        String value = text(fields, first);
        return value.isBlank() ? text(fields, second) : value;
    }

    private static UUID uuid(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value.isBlank()) {
            return NIL_UUID;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return NIL_UUID;
        }
    }

    private static UUID firstUuid(Map<String, String> fields, String first, String second) {
        UUID value = uuid(fields, first);
        return value.equals(NIL_UUID) ? uuid(fields, second) : value;
    }

    private static long longValue(Map<String, String> fields, String key) {
        try {
            return Long.parseLong(text(fields, key));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int intValue(Map<String, String> fields, String key) {
        try {
            return Integer.parseInt(text(fields, key));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean bool(Map<String, String> fields, String key) {
        return Boolean.parseBoolean(text(fields, key));
    }

    private static Boolean nullableBool(Map<String, String> fields, String key) {
        String value = text(fields, key);
        return value.isBlank() ? null : Boolean.parseBoolean(value);
    }

    private static IslandFlag flag(Map<String, String> fields, String key) {
        try {
            return IslandFlag.valueOf(text(fields, key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static IslandRole role(Map<String, String> fields, String key) {
        try {
            return IslandRole.valueOf(text(fields, key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static kr.lunaf.cloudislands.api.model.IslandPermission permission(Map<String, String> fields, String key) {
        try {
            return kr.lunaf.cloudislands.api.model.IslandPermission.valueOf(text(fields, key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static IslandRole firstRole(Map<String, String> fields, String first, String second) {
        IslandRole value = role(fields, first);
        return value == null ? role(fields, second) : value;
    }

    private static BigDecimal decimal(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal decimalOrNull(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static IslandLocation location(Map<String, String> fields) {
        return new IslandLocation(
            firstText(fields, "worldName", "world"),
            doubleValue(fields, "localX"),
            doubleValue(fields, "localY"),
            doubleValue(fields, "localZ"),
            floatValue(fields, "yaw"),
            floatValue(fields, "pitch")
        );
    }

    private static double doubleValue(Map<String, String> fields, String key) {
        try {
            return Double.parseDouble(text(fields, key));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private static float floatValue(Map<String, String> fields, String key) {
        try {
            return Float.parseFloat(text(fields, key));
        } catch (NumberFormatException ignored) {
            return 0F;
        }
    }
}
