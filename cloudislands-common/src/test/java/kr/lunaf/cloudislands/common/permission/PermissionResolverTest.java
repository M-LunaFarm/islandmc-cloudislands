package kr.lunaf.cloudislands.common.permission;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.api.model.RoleId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionResolverTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID VISITOR = UUID.fromString("00000000-0000-0000-0000-000000000403");
    private static final UUID BANNED = UUID.fromString("00000000-0000-0000-0000-000000000404");

    @Test
    void followsCachedPermissionOrderWithoutExternalLookups() {
        CachedPermissionSet permissions = new CachedPermissionSet();
        permissions.put(IslandRole.TRUSTED, IslandPermission.INTERACT, true);
        PermissionResolver resolver = new PermissionResolver(permissions, Map.of(
            OWNER, IslandRole.OWNER,
            TRUSTED, IslandRole.TRUSTED,
            BANNED, IslandRole.BANNED
        ));

        assertAllowed(resolver.check(VISITOR, IslandPermission.BREAK, true), IslandRole.OWNER);
        assertAllowed(resolver.check(OWNER, IslandPermission.BREAK, false), IslandRole.OWNER);
        assertAllowed(resolver.check(TRUSTED, IslandPermission.INTERACT, false), IslandRole.TRUSTED);
        assertDenied(resolver.check(VISITOR, IslandPermission.INTERACT, false), IslandRole.VISITOR);
        assertDenied(resolver.check(BANNED, IslandPermission.INTERACT, false), IslandRole.BANNED);
    }

    @Test
    void resolvesLegacyDefaultsFromRoleKeysWithoutEnumInput() {
        CachedPermissionSet permissions = new CachedPermissionSet();
        permissions.put(IslandRole.TRUSTED, IslandPermission.INTERACT, true);
        PermissionResolver resolver = PermissionResolver.fromRoleKeys(permissions, Map.of(
            OWNER, "owner",
            TRUSTED, "trusted",
            BANNED, "banned"
        ));

        assertAllowed(resolver.check(OWNER, IslandPermission.BREAK, false), IslandRole.OWNER);
        assertAllowed(resolver.check(TRUSTED, IslandPermission.INTERACT, false), IslandRole.TRUSTED);
        assertDenied(resolver.check(BANNED, IslandPermission.INTERACT, false), IslandRole.BANNED);
        assertDenied(resolver.check(VISITOR, IslandPermission.INTERACT, false), IslandRole.VISITOR);
    }

    @Test
    void keepsCustomRoleKeyAsCanonicalPermissionResultIdentity() {
        UUID builder = UUID.fromString("00000000-0000-0000-0000-000000000405");
        PermissionResolver resolver = PermissionResolver.fromRoleKeys(new CachedPermissionSet(), Map.of(builder, "builder"));

        PermissionResult result = resolver.check(builder, IslandPermission.BUILD, false);

        assertFalse(result.allowed());
        assertEquals(RoleId.of("BUILDER"), result.effectiveRoleId());
        assertNull(result.effectiveRole());
    }

    private void assertAllowed(PermissionResult result, IslandRole role) {
        assertTrue(result.allowed());
        assertEquals(role, result.effectiveRole());
        assertEquals(RoleId.of(role, IslandRole.VISITOR.name()), result.effectiveRoleId());
    }

    private void assertDenied(PermissionResult result, IslandRole role) {
        assertFalse(result.allowed());
        assertEquals(role, result.effectiveRole());
        assertEquals(RoleId.of(role, IslandRole.VISITOR.name()), result.effectiveRoleId());
        assertEquals("DEFAULT_DENY", result.reason());
    }
}
