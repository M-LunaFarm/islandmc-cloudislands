package kr.lunaf.cloudislands.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;
import org.junit.jupiter.api.Test;

class CloudEventMapperRoleKeyTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    void preservesCustomRoleKeysAcrossPublicRoleEvents() {
        IslandMemberJoinEvent joined = assertInstanceOf(IslandMemberJoinEvent.class, map("ISLAND_MEMBER_JOINED", Map.of(
            "islandId", ISLAND.toString(),
            "playerUuid", PLAYER.toString(),
            "role", "MEMBER",
            "roleKey", "builder"
        )));
        assertEquals("BUILDER", joined.roleKey());
        assertEquals(RoleId.of("BUILDER"), joined.roleId());
        assertNull(joined.role());

        IslandMemberChangedEvent memberChanged = assertInstanceOf(IslandMemberChangedEvent.class, map("ISLAND_MEMBER_CHANGED", Map.of(
            "islandId", ISLAND.toString(),
            "playerUuid", PLAYER.toString(),
            "action", "ROLE_CHANGED",
            "oldRoleKey", "builder",
            "newRoleKey", "architect"
        )));
        assertEquals(RoleId.of("BUILDER"), memberChanged.oldRoleId());
        assertEquals(RoleId.of("ARCHITECT"), memberChanged.newRoleId());
        assertNull(memberChanged.oldRole());
        assertNull(memberChanged.newRole());

        IslandRoleChangeEvent roleChanged = assertInstanceOf(IslandRoleChangeEvent.class, map("ISLAND_MEMBER_ROLE_CHANGED", Map.of(
            "islandId", ISLAND.toString(),
            "playerUuid", PLAYER.toString(),
            "oldRole", "builder",
            "newRole", "architect"
        )));
        assertEquals("BUILDER", roleChanged.oldRoleKey());
        assertEquals("ARCHITECT", roleChanged.newRoleKey());

        IslandPermissionChangeEvent permissionChanged = assertInstanceOf(IslandPermissionChangeEvent.class, map("ISLAND_PERMISSION_CHANGED", Map.of(
            "islandId", ISLAND.toString(),
            "roleKey", "builder",
            "permission", "BUILD",
            "allowed", "true"
        )));
        assertEquals(RoleId.of("BUILDER"), permissionChanged.roleId());
        assertEquals(IslandPermission.BUILD, permissionChanged.permission());
        assertNull(permissionChanged.role());

        IslandRoleCatalogChangeEvent catalogChanged = assertInstanceOf(IslandRoleCatalogChangeEvent.class, map("ISLAND_ROLE_CHANGED", Map.of(
            "islandId", ISLAND.toString(),
            "targetRole", "architect",
            "operation", "ROLE_UPSERT"
        )));
        assertEquals(RoleId.of("ARCHITECT"), catalogChanged.roleId());
        assertNull(catalogChanged.role());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyEnumConstructorsStillProduceCanonicalRoleIds() {
        IslandMemberJoinEvent joined = new IslandMemberJoinEvent(ISLAND, PLAYER, IslandRole.MEMBER, OCCURRED_AT);
        IslandMemberChangedEvent memberChanged = new IslandMemberChangedEvent(ISLAND, PLAYER, "ROLE_CHANGED", IslandRole.MEMBER, IslandRole.TRUSTED, OCCURRED_AT);
        IslandRoleChangeEvent roleChanged = new IslandRoleChangeEvent(ISLAND, PLAYER, IslandRole.MEMBER, IslandRole.TRUSTED, OCCURRED_AT);
        IslandPermissionChangeEvent permissionChanged = new IslandPermissionChangeEvent(ISLAND, IslandRole.MEMBER, IslandPermission.BUILD, true, OCCURRED_AT);
        IslandRoleCatalogChangeEvent catalogChanged = new IslandRoleCatalogChangeEvent(ISLAND, IslandRole.MEMBER, "ROLE_UPSERT", OCCURRED_AT);

        assertEquals(RoleId.of("MEMBER"), joined.roleId());
        assertEquals(RoleId.of("MEMBER"), memberChanged.oldRoleId());
        assertEquals(RoleId.of("TRUSTED"), memberChanged.newRoleId());
        assertEquals(RoleId.of("MEMBER"), roleChanged.oldRoleId());
        assertEquals(RoleId.of("TRUSTED"), roleChanged.newRoleId());
        assertEquals(RoleId.of("MEMBER"), permissionChanged.roleId());
        assertEquals(RoleId.of("MEMBER"), catalogChanged.roleId());
    }

    private static CloudEvent map(String type, Map<String, String> fields) {
        return CloudEventMapper.map(new GlobalEventSnapshot(type, fields, OCCURRED_AT)).orElseThrow();
    }
}
