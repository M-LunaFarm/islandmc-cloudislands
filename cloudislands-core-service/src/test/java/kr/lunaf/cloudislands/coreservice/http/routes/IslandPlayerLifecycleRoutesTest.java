package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import org.junit.jupiter.api.Test;

class IslandPlayerLifecycleRoutesTest {
    @Test
    void registersIslandPlayerLifecycleEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandPlayerLifecycleRoutes routes = new IslandPlayerLifecycleRoutes(null, null, null, null, (islandId, ownerUuid, requesterUuid, reason) -> false, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/islands/delete"));
        assertTrue(paths.contains("/v1/islands/reset"));
    }

    @Test
    void rendersLifecycleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(
            "{\"accepted\":true,\"code\":\"RESET_ACCEPTED\"}",
            IslandPlayerLifecycleRoutes.lifecycleJson(new IslandLifecycleWorkflow.Result(true, "RESET_ACCEPTED", null))
        );
        assertEquals(
            "{\"accepted\":false,\"code\":\"NOT_OWNER_OR_MISSING\",\"islandId\":\"00000000-0000-0000-0000-000000000001\"}",
            IslandPlayerLifecycleRoutes.deleteResultJson(new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId))
        );
    }
}
