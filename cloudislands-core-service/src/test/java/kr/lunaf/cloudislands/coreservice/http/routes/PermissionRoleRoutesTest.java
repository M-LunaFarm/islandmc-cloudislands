package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import org.junit.jupiter.api.Test;

class PermissionRoleRoutesTest {
    @Test
    void registersPermissionAndRoleEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PermissionRoleRoutes routes = new PermissionRoleRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/islands/permissions"));
        assertTrue(paths.contains("/v1/islands/permissions/set"));
        assertTrue(paths.contains("/v1/islands/permissions/overrides/set"));
        assertTrue(paths.contains("/v1/islands/roles"));
        assertTrue(paths.contains("/v1/islands/roles/upsert"));
        assertTrue(paths.contains("/v1/islands/roles/reset"));
    }

    @Test
    void rendersPermissionAndRoleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(
            "{\"rules\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"MEMBER\",\"roleKey\":\"MEMBER\",\"permission\":\"BUILD\",\"allowed\":true}],\"overrides\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"playerUuid\":\"00000000-0000-0000-0000-000000000002\",\"permission\":\"BREAK\",\"allowed\":false}]}",
            PermissionRoleRoutes.permissionsJson(List.of(new IslandPermissionRuleSnapshot(islandId, IslandRole.MEMBER, IslandPermission.BUILD, true)), List.of(new IslandPermissionOverrideSnapshot(islandId, playerUuid, IslandPermission.BREAK, false)))
        );
        assertEquals(
            "{\"roles\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"weight\":7,\"displayName\":\"Builder \\\"A\\\"\"}]}",
            PermissionRoleRoutes.rolesJson(List.of(new IslandRoleSnapshot(islandId, "builder", 7, "Builder \"A\"")))
        );
        assertEquals(
            "{\"roles\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"weight\":20,\"displayName\":\"Builder\"}]}",
            PermissionRoleRoutes.rolesJson(List.of(new IslandRoleSnapshot(islandId, "builder", 20, "Builder")))
        );
    }
}
