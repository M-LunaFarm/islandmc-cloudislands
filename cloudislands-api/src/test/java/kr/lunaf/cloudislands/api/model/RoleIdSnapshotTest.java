package kr.lunaf.cloudislands.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
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
    void customEnumSlotsAreNotExposedAsRoleIdentities() {
        assertThrows(IllegalArgumentException.class, () -> IslandRole.valueOf("CUSTOM_1"));
        assertThrows(IllegalArgumentException.class, () -> IslandRole.valueOf("CUSTOM_5"));
        assertEquals("CUSTOM_1", RoleId.of("custom-1").value());
    }

    @Test
    void roleDefinitionsExposeCanonicalDefaultMemberCatalogWithoutEnumIdentity() {
        List<RoleDefinition> defaults = RoleDefinition.defaultMemberRoles();

        assertEquals(List.of(
            RoleId.of("CO_OWNER"),
            RoleId.of("MODERATOR"),
            RoleId.of("MEMBER"),
            RoleId.of("TRUSTED")
        ), defaults.stream().map(RoleDefinition::roleId).toList());
        assertEquals(List.of(1, 2, 3, 4), defaults.stream().map(RoleDefinition::weight).toList());
        assertEquals(List.of(true, true, true, true), defaults.stream().map(RoleDefinition::editable).toList());
        assertEquals(List.of(true, true, true, true), defaults.stream().map(RoleDefinition::memberRole).toList());
        assertEquals(4, defaults.stream().filter(definition -> definition.systemRole() == null).count());
    }

    @Test
    void commandServiceExposesRoleIdMutationsAndKeepsEnumAsDeprecatedAdapter() throws Exception {
        assertTrue(IslandRole.class.isAnnotationPresent(Deprecated.class));
        assertEquals(RoleId.class, IslandCommandService.class
            .getMethod("setRoleResult", UUID.class, UUID.class, UUID.class, RoleId.class)
            .getParameterTypes()[3]);
        assertEquals(String.class, IslandCommandService.class
            .getMethod("setPermissionResult", UUID.class, UUID.class, String.class, IslandPermission.class, boolean.class)
            .getParameterTypes()[2]);
        assertEquals(RoleId.class, IslandCommandService.class
            .getMethod("upsertRoleResult", UUID.class, UUID.class, RoleId.class, int.class, String.class)
            .getParameterTypes()[2]);
        assertEquals(String.class, IslandCommandService.class
            .getMethod("resetRoleResult", UUID.class, UUID.class, String.class)
            .getParameterTypes()[2]);
    }
}
