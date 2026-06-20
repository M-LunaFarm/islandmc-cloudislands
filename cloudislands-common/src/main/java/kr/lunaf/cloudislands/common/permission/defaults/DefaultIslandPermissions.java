package kr.lunaf.cloudislands.common.permission.defaults;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;

public final class DefaultIslandPermissions {
    private DefaultIslandPermissions() {}

    public static CachedPermissionSet create() {
        CachedPermissionSet set = new CachedPermissionSet();
        allowMember(set, IslandRole.MEMBER);
        allowMember(set, IslandRole.MODERATOR);
        allowMember(set, IslandRole.CO_OWNER);
        allowTrusted(set);
        allowManagement(set, IslandRole.MODERATOR);
        allowManagement(set, IslandRole.CO_OWNER);
        return set;
    }

    private static void allowMember(CachedPermissionSet set, IslandRole role) {
        for (IslandPermission permission : new IslandPermission[] {
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
            IslandPermission.ATTACK_MOB,
            IslandPermission.PICKUP_ITEM,
            IslandPermission.DROP_ITEM,
            IslandPermission.USE_SPAWNER,
            IslandPermission.USE_ANVIL,
            IslandPermission.USE_ENCHANT_TABLE,
            IslandPermission.USE_BREWING_STAND,
            IslandPermission.SET_HOME,
            IslandPermission.DEPOSIT_BANK
        }) {
            set.put(role, permission, true);
        }
    }

    private static void allowTrusted(CachedPermissionSet set) {
        for (IslandPermission permission : new IslandPermission[] {
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.USE_DOOR,
            IslandPermission.USE_BUTTON,
            IslandPermission.USE_PRESSURE_PLATE,
            IslandPermission.PICKUP_ITEM,
            IslandPermission.DROP_ITEM
        }) {
            set.put(IslandRole.TRUSTED, permission, true);
        }
    }

    private static void allowManagement(CachedPermissionSet set, IslandRole role) {
        for (IslandPermission permission : new IslandPermission[] {
            IslandPermission.MANAGE_MEMBERS,
            IslandPermission.MANAGE_ROLES,
            IslandPermission.MANAGE_FLAGS,
            IslandPermission.MANAGE_WARPS,
            IslandPermission.MANAGE_UPGRADES,
            IslandPermission.START_LEVEL_CALC,
            IslandPermission.BAN_VISITOR,
            IslandPermission.KICK_VISITOR,
            IslandPermission.SET_BIOME,
            IslandPermission.WITHDRAW_BANK
        }) {
            set.put(role, permission, true);
        }
    }
}
