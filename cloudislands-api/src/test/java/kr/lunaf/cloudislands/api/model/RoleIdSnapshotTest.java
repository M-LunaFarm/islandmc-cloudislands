package kr.lunaf.cloudislands.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleIdSnapshotTest {
    @Test
    void roleIdRequiresCanonicalNonBlankValue() {
        assertEquals("BUILDER", new RoleId(" builder ").value());
        assertEquals("CO_OWNER", new RoleId("co-owner").value());
        assertEquals(SystemRole.OWNER, RoleId.of("owner").asSystemRole());
        assertEquals(SystemRole.BANNED, SystemRole.from("banned"));
        assertThrows(IllegalArgumentException.class, () -> new RoleId(" "));
    }

    @Test
    void memberSnapshotAlwaysExposesCanonicalRoleKey() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Instant joinedAt = Instant.parse("2026-01-02T03:04:05Z");

        IslandMemberSnapshot fallback = new IslandMemberSnapshot(islandId, playerUuid, (IslandRole) null, joinedAt, null);
        assertEquals("VISITOR", fallback.effectiveRoleKey());
        assertEquals(RoleId.of("VISITOR"), fallback.roleId());
        assertEquals(IslandRole.VISITOR, fallback.role());
        assertEquals(SystemRole.VISITOR, fallback.systemRole());

        IslandMemberSnapshot dynamic = new IslandMemberSnapshot(islandId, playerUuid, "builder", joinedAt, null);
        assertEquals("BUILDER", dynamic.effectiveRoleKey());
        assertEquals(RoleId.of("BUILDER"), dynamic.roleId());
        assertNull(dynamic.role());
        assertNull(dynamic.systemRole());
    }

    @Test
    void roleAndPermissionSnapshotsAlwaysExposeCanonicalRoleKey() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        IslandPermissionRuleSnapshot permission = new IslandPermissionRuleSnapshot(islandId, "builder", IslandPermission.BUILD, true);
        assertEquals("BUILDER", permission.effectiveRoleKey());
        assertEquals(RoleId.of("BUILDER"), permission.roleId());
        assertNull(permission.role());
        assertNull(permission.systemRole());
    }

    @Test
    void allArgsSnapshotsRejectMissingCanonicalRoleId() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Instant joinedAt = Instant.parse("2026-01-02T03:04:05Z");

        assertThrows(IllegalArgumentException.class, () -> new IslandMemberSnapshot(islandId, playerUuid, null, joinedAt, null, " "));
        assertThrows(IllegalArgumentException.class, () -> new IslandRoleSnapshot(islandId, null, 10, "Broken", ""));
        assertThrows(IllegalArgumentException.class, () -> new IslandPermissionRuleSnapshot(islandId, null, IslandPermission.BUILD, true, ""));
    }

    @Test
    void customEnumSlotsAreDeprecatedCompatibilityOnly() throws Exception {
        assertNotNull(IslandRole.class.getField("CUSTOM_1").getAnnotation(Deprecated.class));
        assertNotNull(IslandRole.class.getField("CUSTOM_5").getAnnotation(Deprecated.class));
    }
}
