package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import org.junit.jupiter.api.Test;

class IslandCatalogRoutesTest {
    @Test
    void registersIslandCatalogEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandCatalogRoutes routes = new IslandCatalogRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/islands/info"));
        assertTrue(paths.contains("/v1/islands/public"));
        assertTrue(paths.contains("/v1/islands"));
    }

    @Test
    void rendersIslandContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandSnapshot island = new IslandSnapshot(
            islandId,
            ownerUuid,
            "Sky \"Base\"",
            IslandState.ACTIVE,
            100,
            7L,
            "12.5",
            true,
            Instant.parse("2026-01-02T03:04:05Z"),
            Instant.parse("2026-01-03T03:04:05Z")
        );

        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"ownerUuid\":\"00000000-0000-0000-0000-000000000002\",\"name\":\"Sky \\\"Base\\\"\",\"state\":\"ACTIVE\",\"size\":100,\"border\":100,\"level\":7,\"worth\":\"12.5\",\"publicAccess\":true,\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T03:04:05Z\"}",
            IslandCatalogRoutes.islandJson(island)
        );
        assertEquals(
            "{\"islands\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"ownerUuid\":\"00000000-0000-0000-0000-000000000002\",\"name\":\"Sky \\\"Base\\\"\",\"state\":\"ACTIVE\",\"size\":100,\"border\":100,\"level\":7,\"worth\":\"12.5\",\"publicAccess\":true,\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T03:04:05Z\"}]}",
            IslandCatalogRoutes.islandsJson(List.of(island))
        );
        assertEquals(
            "{\"accepted\":true,\"code\":\"ACCEPTED\",\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"ticket\":null}",
            IslandCatalogRoutes.createResultJson(new CreateIslandResult(true, "ACCEPTED", island, null))
        );
    }
}
