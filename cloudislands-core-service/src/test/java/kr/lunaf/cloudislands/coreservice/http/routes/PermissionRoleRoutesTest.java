package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
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

        assertEquals(5, paths.size());
        assertTrue(paths.contains("/v1/islands/permissions"));
        assertTrue(paths.contains("/v1/islands/permissions/set"));
        assertTrue(paths.contains("/v1/islands/roles"));
        assertTrue(paths.contains("/v1/islands/roles/upsert"));
        assertTrue(paths.contains("/v1/islands/roles/reset"));
    }

    @Test
    void rendersPermissionAndRoleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(
            "{\"rules\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"MEMBER\",\"permission\":\"BUILD\",\"allowed\":true}]}",
            PermissionRoleRoutes.permissionsJson(List.of(new IslandPermissionRuleSnapshot(islandId, IslandRole.MEMBER, IslandPermission.BUILD, true)))
        );
        assertEquals(
            "{\"roles\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"CUSTOM_1\",\"weight\":7,\"displayName\":\"Builder \\\"A\\\"\"}]}",
            PermissionRoleRoutes.rolesJson(List.of(new IslandRoleSnapshot(islandId, IslandRole.CUSTOM_1, 7, "Builder \"A\"")))
        );
    }
}
