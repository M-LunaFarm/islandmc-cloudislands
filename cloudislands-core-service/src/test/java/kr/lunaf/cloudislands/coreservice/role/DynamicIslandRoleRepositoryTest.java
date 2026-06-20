package kr.lunaf.cloudislands.coreservice.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
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
    void permissionRulesKeepOperatorDefinedRoleKeys() {
        InMemoryIslandPermissionRuleRepository permissions = new InMemoryIslandPermissionRuleRepository();

        permissions.putRoleKey(ISLAND, "builder", IslandPermission.BUILD, true);

        var rule = permissions.list(ISLAND).getFirst();
        assertNull(rule.role());
        assertEquals("BUILDER", rule.effectiveRoleKey());
        assertTrue(rule.allowed());
    }
}
