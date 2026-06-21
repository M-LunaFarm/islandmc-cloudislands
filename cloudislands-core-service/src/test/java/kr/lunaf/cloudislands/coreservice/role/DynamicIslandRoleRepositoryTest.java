package kr.lunaf.cloudislands.coreservice.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import org.junit.jupiter.api.Test;

class DynamicIslandRoleRepositoryTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void roleCatalogKeepsOperatorDefinedRoleKeys() {
        InMemoryIslandRoleRepository roles = new InMemoryIslandRoleRepository();

        roles.upsertKey(ISLAND, "builder", 20, "Builder");

        var dynamic = roles.list(ISLAND).stream()
            .filter(role -> role.effectiveRoleKey().equals("BUILDER"))
            .findFirst()
            .orElseThrow();
        assertNull(dynamic.role());
        assertEquals("BUILDER", dynamic.roleKey());
        assertEquals("Builder", dynamic.displayName());
    }

    @Test
    void roleCatalogDefaultsComeFromRoleDefinitionsInsteadOfEnumIteration() throws Exception {
        InMemoryIslandRoleRepository roles = new InMemoryIslandRoleRepository();

        assertEquals(
            java.util.List.of("CO_OWNER", "MODERATOR", "MEMBER", "TRUSTED"),
            roles.list(ISLAND).stream().map(role -> role.roleId().value()).toList()
        );
        assertFalse(IslandRoleRepository.editableRoleKey("owner"));
        assertFalse(IslandRoleRepository.editableRoleKey("visitor"));
        assertFalse(IslandRoleRepository.editableRoleKey("banned"));
        assertTrue(IslandRoleRepository.editableRoleKey("builder"));

        String repository = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/role/IslandRoleRepository.java"));
        assertFalse(repository.contains("IslandRole.values()"), "Core role catalog must not iterate enum identities");
        assertFalse(repository.contains("IslandRole role"), "Core role mutation contract must use role keys, not enum identities");
        assertTrue(repository.contains("RoleDefinition.defaultMemberRoles()"), "Core role catalog must use RoleId-backed role definitions");
    }

    @Test
    void permissionRulesKeepOperatorDefinedRoleKeys() {
        InMemoryIslandPermissionRuleRepository permissions = new InMemoryIslandPermissionRuleRepository();

        permissions.putRoleKey(ISLAND, "builder", IslandPermission.BUILD, true);

        var rule = permissions.list(ISLAND).getFirst();
        assertNull(rule.role());
        assertEquals("BUILDER", rule.effectiveRoleKey());
        assertTrue(rule.allowed());
    }

    @Test
    void memberAssignmentsKeepOperatorDefinedRoleKeys() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000112");

        metadata.upsertMemberKey(ISLAND, playerUuid, "builder");

        var member = metadata.members(ISLAND).getFirst();
        assertNull(member.role());
        assertEquals("BUILDER", member.roleKey());
        assertEquals("BUILDER", member.effectiveRoleKey());
    }
}
