package kr.lunaf.cloudislands.common.permission;

import java.util.List;
import java.util.Set;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;

public final class IslandPermissionSystemPolicy {
    public static final String DECISION_ORDER =
            "admin-bypass>island-owner>explicit-member-role>trusted-override>visitor-flags>default-deny";
    public static final String SYNC_EVENT_POLICY =
            "permission-decisions-on-paper-events-use-region-index-and-local-permission-cache-only";
    public static final String DEFAULT_DENY_POLICY =
            "unknown-player-unknown-role-and-unset-permission-deny-by-default";

    private static final List<String> DECISION_STEPS = List.of(
            "admin-bypass",
            "island-owner",
            "explicit-member-role",
            "trusted-override",
            "visitor-flags",
            "default-deny"
    );

    private static final Set<IslandRole> BASE_ROLES = Set.of(
            IslandRole.OWNER,
            IslandRole.CO_OWNER,
            IslandRole.MODERATOR,
            IslandRole.MEMBER,
            IslandRole.TRUSTED,
            IslandRole.VISITOR,
            IslandRole.BANNED
    );

    private static final Set<IslandPermission> BASE_PERMISSIONS = Set.of(
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.OPEN_CONTAINER,
            IslandPermission.USE_DOOR,
            IslandPermission.USE_BUTTON,
            IslandPermission.USE_PRESSURE_PLATE,
            IslandPermission.USE_REDSTONE,
            IslandPermission.PLACE_LIQUID,
            IslandPermission.BREAK_LIQUID,
            IslandPermission.ATTACK_PLAYER,
            IslandPermission.ATTACK_MOB,
            IslandPermission.PICKUP_ITEM,
            IslandPermission.DROP_ITEM,
            IslandPermission.USE_SPAWNER,
            IslandPermission.USE_ANVIL,
            IslandPermission.USE_ENCHANT_TABLE,
            IslandPermission.USE_BREWING_STAND,
            IslandPermission.MANAGE_MEMBERS,
            IslandPermission.MANAGE_ROLES,
            IslandPermission.MANAGE_FLAGS,
            IslandPermission.MANAGE_WARPS,
            IslandPermission.MANAGE_UPGRADES,
            IslandPermission.START_LEVEL_CALC,
            IslandPermission.BAN_VISITOR,
            IslandPermission.KICK_VISITOR,
            IslandPermission.SET_HOME,
            IslandPermission.SET_BIOME,
            IslandPermission.WITHDRAW_BANK,
            IslandPermission.DEPOSIT_BANK
    );

    private static final Set<IslandFlag> BASE_FLAGS = Set.of(
            IslandFlag.PVP,
            IslandFlag.MOB_SPAWN,
            IslandFlag.ANIMAL_SPAWN,
            IslandFlag.MONSTER_SPAWN,
            IslandFlag.FIRE_SPREAD,
            IslandFlag.EXPLOSION,
            IslandFlag.CREEPER_DAMAGE,
            IslandFlag.TNT_DAMAGE,
            IslandFlag.WITHER_DAMAGE,
            IslandFlag.ENDERMAN_GRIEF,
            IslandFlag.WATER_FLOW,
            IslandFlag.LAVA_FLOW,
            IslandFlag.ICE_MELT,
            IslandFlag.LEAF_DECAY,
            IslandFlag.VISITOR_INTERACT,
            IslandFlag.VISITOR_CONTAINER,
            IslandFlag.VISITOR_PICKUP,
            IslandFlag.VISITOR_DROP,
            IslandFlag.VISITOR_PVP,
            IslandFlag.FLY,
            IslandFlag.KEEP_INVENTORY,
            IslandFlag.PUBLIC_WARPS
    );

    private IslandPermissionSystemPolicy() {
    }

    public static List<String> decisionSteps() {
        return DECISION_STEPS;
    }

    public static Set<IslandRole> baseRoles() {
        return BASE_ROLES;
    }

    public static Set<IslandPermission> basePermissions() {
        return BASE_PERMISSIONS;
    }

    public static Set<IslandFlag> baseFlags() {
        return BASE_FLAGS;
    }

    public static boolean isBaseRole(IslandRole role) {
        return BASE_ROLES.contains(role);
    }

    public static boolean isBasePermission(IslandPermission permission) {
        return BASE_PERMISSIONS.contains(permission);
    }

    public static boolean isBaseFlag(IslandFlag flag) {
        return BASE_FLAGS.contains(flag);
    }
}
