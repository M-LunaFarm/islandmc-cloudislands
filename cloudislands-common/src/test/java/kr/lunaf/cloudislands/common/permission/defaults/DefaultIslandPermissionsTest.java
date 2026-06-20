package kr.lunaf.cloudislands.common.permission.defaults;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultIslandPermissionsTest {
    @Test
    void visitorsAndBannedPlayersDefaultDenyEveryPermissionKey() {
        CachedPermissionSet permissions = DefaultIslandPermissions.create();

        for (IslandPermission permission : IslandPermission.values()) {
            assertFalse(permissions.allowed(IslandRole.VISITOR, permission), permission.name());
            assertFalse(permissions.allowed(IslandRole.BANNED, permission), permission.name());
        }
    }

    @Test
    void trustedRoleOnlyGetsSafeInteractionOverrides() {
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        EnumSet<IslandPermission> trustedAllowed = EnumSet.of(
            IslandPermission.BUILD,
            IslandPermission.BREAK,
            IslandPermission.INTERACT,
            IslandPermission.USE_DOOR,
            IslandPermission.USE_BUTTON,
            IslandPermission.USE_PRESSURE_PLATE,
            IslandPermission.PICKUP_ITEM,
            IslandPermission.DROP_ITEM
        );

        for (IslandPermission permission : IslandPermission.values()) {
            if (trustedAllowed.contains(permission)) {
                assertTrue(permissions.allowed(IslandRole.TRUSTED, permission), permission.name());
            } else {
                assertFalse(permissions.allowed(IslandRole.TRUSTED, permission), permission.name());
            }
        }
    }

    @Test
    void moderatorGetsManagementPermissionsButMemberDoesNot() {
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (IslandPermission permission : EnumSet.of(
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
        )) {
            assertTrue(permissions.allowed(IslandRole.MODERATOR, permission), permission.name());
            assertFalse(permissions.allowed(IslandRole.MEMBER, permission), permission.name());
        }
    }

    @Test
    void legacyCustomSlotsAreNotDefaultPermissionRoles() {
        assertThrows(IllegalArgumentException.class, () -> IslandRole.valueOf("CUSTOM_1"));
        assertThrows(IllegalArgumentException.class, () -> IslandRole.valueOf("CUSTOM_5"));
    }
}
