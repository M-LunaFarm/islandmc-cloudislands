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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpException;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionService;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
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

    @Test
    void completeRouteCommitsCompletionBeforeAcknowledgingClaimedJob() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/JobRoutes.java"));
        int completionIndex = source.indexOf("completion.completed(claimed.get())");
        int ackIndex = source.indexOf("jobs.complete(request.nodeId(), request.jobId())");

        assertTrue(completionIndex > 0, "completion must be explicitly committed");
        assertTrue(ackIndex > completionIndex, "job queue ack must happen only after completion state is committed");
        assertTrue(source.contains("\"JOB_COMPLETION_FAILED\""), "completion commit failure must keep the job claimed for retry");
    }

    @Test
    void completeRouteLeavesClaimedJobWhenCompletionCommitFails() throws Exception {
        String nodeId = "island-node-1";
        UUID islandId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        jobs.publish(new IslandJob(jobId, IslandJobType.SAVE_ISLAND, islandId, nodeId, 0, Map.of("fencingToken", "7"), Instant.EPOCH));
        assertEquals(1, jobs.claim(nodeId, List.of(IslandJobType.SAVE_ISLAND), 1).size());

        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        runtimes.markActive(islandId, nodeId, "ci_shard_001", 0, 0, 7L);
        JobCompletionService completion = new JobCompletionService(
            runtimes,
            new InMemoryGlobalEventPublisher(),
            new FailingSnapshotRepository(),
            new InMemoryRouteTicketStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        );
        Map<String, HttpHandler> handlers = new HashMap<>();
        new JobRoutes(jobs, completion, null).register(handlers::put);

        TestExchange exchange = exchange("{\"nodeId\":\"" + nodeId + "\",\"jobId\":\"" + jobId + "\",\"payload\":{\"snapshotNo\":\"12\",\"checksum\":\"sha256:test\",\"sizeBytes\":\"64\"}}");
        handlers.get("/v1/jobs/complete").handle(exchange);

        assertEquals(500, exchange.status());
        assertTrue(exchange.body().contains("\"code\":\"JOB_COMPLETION_FAILED\""));
        assertTrue(jobs.findClaimed(jobId).isPresent(), "claimed job must remain retryable until completion state is committed");
        assertEquals(1L, jobs.countsByState().get("CLAIMED"));
        assertEquals(0L, jobs.countsByState().get("COMPLETED"));
    }

    @Test
    void claimRouteRejectsInvalidTypedRequestFields() throws Exception {
        Map<String, HttpHandler> handlers = new HashMap<>();
        new JobRoutes(new InMemoryIslandJobPublisher(), null, null).register(handlers::put);
        TestExchange exchange = exchange("{\"nodeId\":\"worker-1\",\"maxJobs\":\"many\"}");

        CoreHttpException exception = org.junit.jupiter.api.Assertions.assertThrows(
            CoreHttpException.class,
            () -> handlers.get("/v1/jobs/claim").handle(exchange)
        );

        assertEquals(400, exception.status());
        assertEquals("INVALID_REQUEST", exception.code());
    }

    private TestExchange exchange(String body) {
        return new TestExchange(body);
    }

    private static final class FailingSnapshotRepository implements IslandSnapshotRepository {
        @Override
        public IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes) {
            throw new IllegalStateException("snapshot repository unavailable");
        }

        @Override
        public List<IslandSnapshotRecord> list(UUID islandId, int limit) {
            return List.of();
        }

        @Override
        public Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo) {
            return Optional.empty();
        }

        @Override
        public int prune(UUID islandId, int keepLatest) {
            return 0;
        }

        @Override
        public int pruneRetaining(UUID islandId, Set<Long> retainedSnapshotNos) {
            return 0;
        }
    }

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int status;

        private TestExchange(String body) {
            this.requestBody = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
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
            return URI.create("/test");
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
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }
}
