package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
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
    void registersIslandCatalogEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandCatalogRoutes(null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/public"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands"));
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
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        profiles.touch(ownerUuid, "IslandOwner");

        Map<?, ?> renderedIsland = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandJson(island, null, null, profiles)));
        Map<?, ?> islands = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandsJson(List.of(island), null, null, profiles)));
        Map<?, ?> listedIsland = SimpleJson.object(SimpleJson.list(islands.get("islands")).get(0));
        Map<?, ?> created = SimpleJson.object(SimpleJson.parse(
            IslandCatalogRoutes.createResultJson(new CreateIslandResult(true, "ACCEPTED", island, null))
        ));

        assertIsland(islandId, ownerUuid, renderedIsland);
        assertIsland(islandId, ownerUuid, listedIsland);
        assertEquals(true, created.get("accepted"));
        assertEquals("ACCEPTED", SimpleJson.text(created.get("code")));
        assertEquals(islandId.toString(), SimpleJson.text(created.get("islandId")));
        assertEquals(null, created.get("ticket"));
    }

    @Test
    void rendersAuthoritativeBorderLimitIndependentlyFromProtectionSize() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000012");
        IslandSnapshot island = new IslandSnapshot(
            islandId, ownerUuid, "Independent border", IslandState.ACTIVE, 300, 0L, "0", true, Instant.EPOCH, Instant.EPOCH
        );
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        limits.set(islandId, "BORDER", 450L, ownerUuid);

        Map<?, ?> rendered = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandJson(island, limits)));

        assertEquals(300, ((Number) rendered.get("size")).intValue());
        assertEquals(450L, ((Number) rendered.get("border")).longValue());
    }

    @Test
    void rendersPublicIslandDescriptionFromAuthoritativeMetadata() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        IslandSnapshot island = new IslandSnapshot(
            islandId, UUID.fromString("00000000-0000-0000-0000-000000000022"), "Profiled", IslandState.ACTIVE, 100, 1L, "2", true, Instant.EPOCH, Instant.EPOCH
        );
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        metadata.setFlag(islandId, IslandFlag.PROFILE_DESCRIPTION, "Public island description");

        Map<?, ?> rendered = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandJson(island, null, metadata)));

        assertEquals("Public island description", SimpleJson.text(rendered.get("description")));
    }

    @Test
    void boundsProfileTextAtTheCoreTrustBoundary() {
        assertTrue(IslandSettingsRoutes.validProfileValue(IslandFlag.PROFILE_DESCRIPTION, "x".repeat(256)));
        assertFalse(IslandSettingsRoutes.validProfileValue(IslandFlag.PROFILE_DESCRIPTION, "x".repeat(257)));
        assertTrue(IslandSettingsRoutes.validProfileValue(IslandFlag.SOCIAL_DISCORD, "x".repeat(128)));
        assertFalse(IslandSettingsRoutes.validProfileValue(IslandFlag.SOCIAL_PAYPAL, "x".repeat(129)));
        assertFalse(IslandSettingsRoutes.validProfileValue(IslandFlag.PROFILE_DESCRIPTION, "line\nfeed"));
        assertTrue(IslandSettingsRoutes.validProfileValue(IslandFlag.PVP, "arbitrary-policy-value"));
    }

    private static void assertIsland(UUID islandId, UUID ownerUuid, Map<?, ?> island) {
        assertEquals(islandId.toString(), SimpleJson.text(island.get("islandId")));
        assertEquals(ownerUuid.toString(), SimpleJson.text(island.get("ownerUuid")));
        assertEquals("IslandOwner", SimpleJson.text(island.get("ownerName")));
        assertEquals("Sky \"Base\"", SimpleJson.text(island.get("name")));
        assertEquals("", SimpleJson.text(island.get("description")));
        assertEquals("ACTIVE", SimpleJson.text(island.get("state")));
        assertEquals(100, ((Number) island.get("size")).intValue());
        assertEquals(100, ((Number) island.get("border")).intValue());
        assertEquals(7L, ((Number) island.get("level")).longValue());
        assertEquals("12.5", SimpleJson.text(island.get("worth")));
        assertEquals(true, island.get("publicAccess"));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(island.get("createdAt")));
        assertEquals("2026-01-03T03:04:05Z", SimpleJson.text(island.get("updatedAt")));
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
