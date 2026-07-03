package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

class AdminIslandLifecycleRoutesTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000802");

    @Test
    void registersAdminIslandLifecycleRoutesAsPostOnly() {
        RecordingRegistry routes = new RecordingRegistry();
        RecordingRegistry prefixes = new RecordingRegistry();

        new AdminIslandLifecycleRoutes(null, null, null, null, null, null, null).register(routes, prefixes);

        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/activate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/deactivate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/migrate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/save"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/snapshot"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/restore"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/rollback"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/quarantine"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/info"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/where"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/delete"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/repair"));
        assertEquals(Set.of("POST"), prefixes.methods("/v1/admin/islands/"));
    }

    @Test
    void restoreRouteQueuesRouteSafeRestoreJobFromRecordedSnapshot() throws Exception {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(ISLAND, OWNER, "default", "route restore target");
        islands.setState(ISLAND, IslandState.ACTIVE);
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 42L);
        snapshots.record(ISLAND, 9L, "islands/" + ISLAND + "/snapshots/000009/bundle.tar.zst", "manual", OWNER, "abc123def456", 2048L, "island-2");

        routes(islands, runtimes, snapshots, jobs).register(handlers::put);
        TestExchange exchange = new TestExchange("{\"islandId\":\"" + ISLAND + "\",\"snapshotNo\":9}");

        handlers.get("/v1/admin/islands/restore").handle(exchange);

        assertEquals(202, exchange.status());
        Map<?, ?> response = SimpleJson.object(SimpleJson.parse(exchange.body()));
        assertEquals(true, response.get("accepted"));
        assertEquals("RESTORE_QUEUED", SimpleJson.text(response.get("code")));
        assertEquals(9L, ((Number) response.get("snapshotNo")).longValue());
        assertEquals("verify-manifest-checksum", SimpleJson.text(response.get("restoreChecksumPolicy")));
        List<IslandJob> queued = jobs.snapshot();
        assertEquals(1, queued.size());
        IslandJob restore = queued.get(0);
        assertEquals(IslandJobType.RESTORE_ISLAND, restore.type());
        assertEquals("island-2", restore.targetNode());
        assertEquals("true", restore.payload().get("preRestoreSnapshotRequired"));
        assertEquals("true", restore.payload().get("transferActivePlayersToLobby"));
        assertEquals("true", restore.payload().get("resetRuntimeBeforeReactivate"));
        assertEquals("true", restore.payload().get("reactivateAfterRestore"));
        assertEquals("ci_shard_002", restore.payload().get("worldName"));
        assertEquals("7", restore.payload().get("cellX"));
        assertEquals("8", restore.payload().get("cellZ"));
        assertEquals(IslandState.RESTORING, runtimes.find(ISLAND).orElseThrow().state());
    }

    @Test
    void failedActiveRestorePreservesExistingRuntimePlacement() throws Exception {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandSnapshotRepository snapshots = new InMemoryIslandSnapshotRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(ISLAND, OWNER, "default", "preserved restore target");
        islands.setState(ISLAND, IslandState.ACTIVE);
        runtimes.markActive(ISLAND, "island-2", "ci_shard_002", 7, 8, 42L);
        snapshots.record(ISLAND, 10L, "islands/" + ISLAND + "/snapshots/000010/bundle.tar.zst", "manual", OWNER, "def456abc123", 4096L, "island-2");

        routes(islands, runtimes, snapshots, job -> {
            throw new IllegalStateException("queue unavailable");
        }).register(handlers::put);
        TestExchange exchange = new TestExchange("{\"islandId\":\"" + ISLAND + "\",\"snapshotNo\":10}");

        handlers.get("/v1/admin/islands/restore").handle(exchange);

        assertEquals(409, exchange.status());
        Map<?, ?> response = SimpleJson.object(SimpleJson.parse(exchange.body()));
        assertEquals(false, response.get("accepted"));
        assertEquals("JOB_QUEUE_UNAVAILABLE", SimpleJson.text(response.get("code")));
        var runtime = runtimes.find(ISLAND).orElseThrow();
        assertEquals(IslandState.ACTIVE, runtime.state());
        assertEquals("island-2", runtime.activeNode());
        assertEquals("ci_shard_002", runtime.activeWorld());
        assertEquals(7, runtime.cellX());
        assertEquals(8, runtime.cellZ());
        assertEquals(42L, runtime.fencingToken());
        assertEquals(IslandState.ACTIVE, islands.findById(ISLAND).orElseThrow().state());
    }

    @Test
    void snapshotRestoreUxCoverageGateTracksEditPlanRequirements() throws Exception {
        String handler = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminIslandLifecycleRoutes.java"));
        String workflowTest = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflowRestoreTest.java"));
        String paperCommand = java.nio.file.Files.readString(java.nio.file.Path.of("../cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandSnapshotCommandHandler.java"));
        String paperMenu = java.nio.file.Files.readString(java.nio.file.Path.of("../cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandSnapshotMenu.java"));

        assertTrue(handler.contains("/v1/admin/islands/restore"), "Core restore route must be explicit.");
        assertTrue(handler.contains("restoreChecksumPolicy"), "Restore route response must expose manifest checksum policy.");
        assertTrue(workflowTest.contains("activeRestoreQueuesPreRestoreSnapshotAndLobbyTransferPolicy"), "Restore workflow must exercise route-safe active restore policy.");
        assertTrue(paperCommand.contains("island.snapshot.restore.confirm"), "Snapshot restore must require confirmation token.");
        assertTrue(paperCommand.contains("checksum="), "Snapshot restore preview must include checksum context.");
        assertTrue(paperMenu.contains("snapshot-menu-checksum"), "Snapshot list GUI must show checksum.");
        assertTrue(paperMenu.contains("snapshot-menu-retention-summary"), "Snapshot GUI must show retention policy.");
        assertTrue(paperMenu.contains("Shift+우클릭"), "Snapshot GUI must require deliberate restore gesture.");
    }

    private static AdminIslandLifecycleRoutes routes(
        InMemoryIslandRepository islands,
        InMemoryIslandRuntimeRepository runtimes,
        InMemoryIslandSnapshotRepository snapshots,
        IslandJobPublisher jobs
    ) {
        IslandLifecycleWorkflow lifecycle = new IslandLifecycleWorkflow(
            runtimes,
            islands,
            new InMemoryIslandTemplateRepository(),
            new InMemoryNodeRegistry(3),
            new NodeAllocator(Duration.ofSeconds(5)),
            jobs,
            new InMemoryGlobalEventPublisher()
        );
        return new AdminIslandLifecycleRoutes(lifecycle, islands, runtimes, snapshots, new InMemoryAuditLogger(), new InMemoryGlobalEventPublisher(), null);
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

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int status;

        private TestExchange(String body) {
            this.requestBody = new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/v1/admin/islands/restore");
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            this.status = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private int status() {
            return status;
        }

        private String body() {
            return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
