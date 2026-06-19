package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

class JobRoutesTest {
    @Test
    void registersJobEndpointGroup() {
        List<String> paths = new ArrayList<>();
        JobRoutes routes = new JobRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/jobs/claim"));
        assertTrue(paths.contains("/v1/admin/jobs/cancel"));
    }

    @Test
    void parsesWorkerSupportedJobTypes() {
        List<IslandJobType> types = JobRoutes.supportedJobTypes("create_island, RESTORE_ISLAND,unknown");

        assertEquals(List.of(IslandJobType.CREATE_ISLAND, IslandJobType.RESTORE_ISLAND), types);
    }

    @Test
    void fallsBackToDefaultWorkerJobTypes() {
        List<IslandJobType> types = JobRoutes.supportedJobTypes("unknown");

        assertTrue(types.contains(IslandJobType.CREATE_ISLAND));
        assertTrue(types.contains(IslandJobType.RESET_ISLAND));
    }
}
