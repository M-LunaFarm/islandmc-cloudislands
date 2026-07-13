package kr.lunaf.cloudislands.coreservice.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.CoreApiAuthGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.ForwardedClientIpResolver;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;
import org.junit.jupiter.api.Test;

class CoreHttpRouteRegistrarTest {
    @Test
    void idempotencyKeyReplaysCompletedMutationWithoutRunningHandlerTwice() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            server.registrar().routePost("/v1/mutate", exchange -> {
                String body = CoreHttpResponses.readBody(exchange);
                CoreHttpResponses.write(exchange, 202, "{\"mutation\":" + mutations.incrementAndGet() + ",\"body\":" + quote(body) + "}");
            });

            HttpRequest firstRequest = server.authorized(HttpRequest.newBuilder(server.uri("/v1/mutate")))
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":10}"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "warehouse-123")
                .build();
            HttpRequest replayRequest = server.authorized(HttpRequest.newBuilder(server.uri("/v1/mutate")))
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":10}"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "warehouse-123")
                .build();

            HttpResponse<String> first = server.request(firstRequest);
            HttpResponse<String> replay = server.request(replayRequest);

            assertEquals(202, first.statusCode());
            assertEquals(first.body(), replay.body());
            assertEquals("false", first.headers().firstValue("X-CloudIslands-Idempotent-Replay").orElse(""));
            assertEquals("true", replay.headers().firstValue("X-CloudIslands-Idempotent-Replay").orElse(""));
            assertEquals(1, mutations.get());
        }
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentMutationPayload() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            server.registrar().routePost("/v1/mutate", exchange -> {
                CoreHttpResponses.readBody(exchange);
                mutations.incrementAndGet();
                CoreHttpResponses.write(exchange, 202, "{\"accepted\":true}");
            });

            HttpResponse<String> first = server.postIdempotent("/v1/mutate", "{\"amount\":10}", "shared-key");
            HttpResponse<String> conflict = server.postIdempotent("/v1/mutate", "{\"amount\":20}", "shared-key");

            assertEquals(202, first.statusCode());
            assertEquals(409, conflict.statusCode());
            assertTrue(conflict.body().contains("IDEMPOTENCY_KEY_REUSED"));
            assertEquals(1, mutations.get());
        }
    }

    @Test
    void concurrentDuplicateWaitsForOwnerAndReplaysItsReceipt() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            CountDownLatch entered = new CountDownLatch(1);
            server.registrar().routePost("/v1/slow-mutate", exchange -> {
                CoreHttpResponses.readBody(exchange);
                int mutation = mutations.incrementAndGet();
                entered.countDown();
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                CoreHttpResponses.write(exchange, 202, "{\"mutation\":" + mutation + "}");
            });
            HttpRequest request = server.authorized(HttpRequest.newBuilder(server.uri("/v1/slow-mutate")))
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":10}"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "concurrent-key")
                .build();

            CompletableFuture<HttpResponse<String>> owner = server.requestAsync(request);
            assertTrue(entered.await(1L, TimeUnit.SECONDS));
            HttpResponse<String> duplicate = server.request(request);
            HttpResponse<String> first = owner.join();

            assertEquals(202, first.statusCode());
            assertEquals(first.body(), duplicate.body());
            assertEquals("true", duplicate.headers().firstValue("X-CloudIslands-Idempotent-Replay").orElse(""));
            assertEquals(1, mutations.get());
        }
    }

    @Test
    void invalidIdempotencyKeyFailsBeforeMutation() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            server.registrar().routePost("/v1/mutate", exchange -> {
                mutations.incrementAndGet();
                CoreHttpResponses.write(exchange, 202, "{\"accepted\":true}");
            });

            HttpResponse<String> response = server.postIdempotent("/v1/mutate", "{}", "contains spaces");

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("INVALID_IDEMPOTENCY_KEY"));
            assertEquals(0, mutations.get());
        }
    }

    @Test
    void idempotentRequestStillUsesTheGlobalBodyLimit() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            server.registrar().routePost("/v1/mutate", exchange -> {
                mutations.incrementAndGet();
                CoreHttpResponses.write(exchange, 202, "{\"accepted\":true}");
            });

            String oversized = "x".repeat(CoreHttpResponses.MAX_REQUEST_BODY_BYTES + 1);
            HttpResponse<String> response = server.postIdempotent("/v1/mutate", oversized, "oversized-key");

            assertEquals(413, response.statusCode());
            assertTrue(response.body().contains("REQUEST_BODY_TOO_LARGE"));
            assertEquals(0, mutations.get());
        }
    }

    @Test
    void deleteMutationIsIdempotentAndIncludesQueryInItsFingerprint() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            AtomicInteger mutations = new AtomicInteger();
            server.registrar().routeMethods("/v1/islands/example", exchange ->
                CoreHttpResponses.write(exchange, 202, "{\"mutation\":" + mutations.incrementAndGet() + "}"), "DELETE");

            HttpRequest firstRequest = server.authorized(HttpRequest.newBuilder(server.uri("/v1/islands/example?requesterUuid=one")))
                .DELETE()
                .header("Idempotency-Key", "delete-key")
                .build();
            HttpRequest differentQuery = server.authorized(HttpRequest.newBuilder(server.uri("/v1/islands/example?requesterUuid=two")))
                .DELETE()
                .header("Idempotency-Key", "delete-key")
                .build();

            HttpResponse<String> first = server.request(firstRequest);
            HttpResponse<String> replay = server.request(firstRequest);
            HttpResponse<String> conflict = server.request(differentQuery);

            assertEquals(202, first.statusCode());
            assertEquals(first.body(), replay.body());
            assertEquals(409, conflict.statusCode());
            assertTrue(conflict.body().contains("IDEMPOTENCY_KEY_REUSED"));
            assertEquals(1, mutations.get());
        }
    }

    @Test
    void exactRoutesDoNotMatchLongerClaimStylePaths() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> exact = server.get("/v1/jobs/claim");
            HttpResponse<String> claimXyz = server.get("/v1/jobs/claimXYZ");

            assertEquals(200, exact.statusCode());
            assertEquals(404, claimXyz.statusCode());
            assertTrue(claimXyz.body().contains("NOT_FOUND"));
        }
    }

    @Test
    void prefixRoutesMatchOnlyPathSegments() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().routePrefix("/v1/islands", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            assertEquals(200, server.get("/v1/islands/abc").statusCode());
            assertEquals(404, server.get("/v1/islandsXYZ").statusCode());
        }
    }

    @Test
    void unsupportedMethodsReturnAllowHeader() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/health", exchange -> CoreHttpResponses.write(exchange, 200, "{\"status\":\"UP\"}"));

            HttpResponse<String> response = server.request(
                HttpRequest.newBuilder(server.uri("/health")).method("DELETE", HttpRequest.BodyPublishers.noBody()).build()
            );

            assertEquals(405, response.statusCode());
            assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(""));
            assertTrue(response.body().contains("METHOD_NOT_ALLOWED"));
        }
    }

    @Test
    void postOnlyRoutesRejectGetWithPostAllowHeader() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().routePost("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> get = server.get("/v1/jobs/claim");
            HttpResponse<String> post = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json")
                    .build()
            );

            assertEquals(405, get.statusCode());
            assertEquals("POST", get.headers().firstValue("Allow").orElse(""));
            assertTrue(get.body().contains("METHOD_NOT_ALLOWED"));
            assertEquals(200, post.statusCode());
        }
    }

    @Test
    void postRequestsRequireJsonContentType() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "text/plain")
                    .build()
            );

            assertEquals(415, response.statusCode());
            assertTrue(response.body().contains("UNSUPPORTED_MEDIA_TYPE"));
        }
    }

    @Test
    void postRequestsRejectJsonPrefixContentTypes() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/jsonevil")
                    .build()
            );

            assertEquals(415, response.statusCode());
            assertTrue(response.body().contains("UNSUPPORTED_MEDIA_TYPE"));
        }
    }

    @Test
    void postRequestsAllowJsonContentTypeParameters() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build()
            );

            assertEquals(200, response.statusCode());
        }
    }

    @Test
    void readBodyRejectsOversizedPayloads() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/body", exchange -> {
                CoreHttpResponses.readBody(exchange, 8);
                CoreHttpResponses.write(exchange, 200, "{\"ok\":true}");
            });

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/body")))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"too\":\"large\"}"))
                    .header("Content-Type", "application/json")
                    .build()
            );

            assertEquals(413, response.statusCode());
            assertTrue(response.body().contains("REQUEST_BODY_TOO_LARGE"));
        }
    }

    @Test
    void normalizedPathUsesActualRequestPathForRouting() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            assertEquals(200, server.get("/v1/jobs/./claim").statusCode());
        }
    }

    @Test
    void trustedForwardedClientIpDrivesIpAllowlist() throws Exception {
        try (ServerFixture server = ServerFixture.start(new IpAllowlist("203.0.113.7"), new ForwardedClientIpResolver("127.0.0.1"))) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> forwarded = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .header("X-Forwarded-For", "203.0.113.7")
                    .GET()
                    .build()
            );
            HttpResponse<String> direct = server.get("/v1/jobs/claim");

            assertEquals(200, forwarded.statusCode());
            assertEquals(403, direct.statusCode());
            assertTrue(direct.body().contains("IP_NOT_ALLOWED"));
        }
    }

    @Test
    void untrustedForwardedHeadersAreRejected() throws Exception {
        try (ServerFixture server = ServerFixture.start(new IpAllowlist(""), new ForwardedClientIpResolver("10.0.0.0/8"))) {
            server.registrar().route("/v1/jobs/claim", exchange -> CoreHttpResponses.write(exchange, 200, "{\"ok\":true}"));

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/jobs/claim")))
                    .header("X-Forwarded-For", "203.0.113.7")
                    .GET()
                    .build()
            );

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("FORWARDED_HEADER_UNTRUSTED"));
        }
    }

    @Test
    void routeRuntimeFailuresReturnJsonError() throws Exception {
        try (ServerFixture server = ServerFixture.start()) {
            server.registrar().routePost("/v1/fails", exchange -> {
                throw new IllegalStateException("repository failed");
            });

            HttpResponse<String> response = server.request(
                server.authorized(HttpRequest.newBuilder(server.uri("/v1/fails")))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json")
                    .build()
            );

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("CORE_ROUTE_FAILED"));
        }
    }

    private static final class ServerFixture implements AutoCloseable {
        private final HttpServer server;
        private final CoreHttpRouteRegistrar registrar;
        private final HttpClient client = HttpClient.newHttpClient();

        private ServerFixture(HttpServer server, CoreHttpRouteRegistrar registrar) {
            this.server = server;
            this.registrar = registrar;
        }

        static ServerFixture start() throws Exception {
            return start(new IpAllowlist(""), new ForwardedClientIpResolver("127.0.0.1,localhost,::1"));
        }

        static ServerFixture start(IpAllowlist ipAllowlist, ForwardedClientIpResolver clientIpResolver) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ApiTokenGuard tokenGuard = new ApiTokenGuard("core-secret");
            MtlsHeaderGuard mtlsGuard = new MtlsHeaderGuard(false, "", "");
            CoreHttpRouteRegistrar registrar = new CoreHttpRouteRegistrar(
                new FixedWindowRateLimiter(Clock.systemUTC(), 100, 60_000L),
                CoreApiAuthGuard.mtlsOrToken(tokenGuard, mtlsGuard),
                clientIpResolver,
                ipAllowlist,
                new AdminEndpointGuard("admin-secret", true, "*")
            );
            registrar.attach(server);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return new ServerFixture(server, registrar);
        }

        CoreHttpRouteRegistrar registrar() {
            return registrar;
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        HttpResponse<String> get(String path) throws Exception {
            return request(authorized(HttpRequest.newBuilder(uri(path))).GET().build());
        }

        HttpResponse<String> request(HttpRequest request) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        CompletableFuture<HttpResponse<String>> requestAsync(HttpRequest request) {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postIdempotent(String path, String body, String key) throws Exception {
            return request(authorized(HttpRequest.newBuilder(uri(path)))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .build());
        }

        HttpRequest.Builder authorized(HttpRequest.Builder builder) {
            return builder
                .header("Authorization", "Bearer core-secret")
                .header("X-CloudIslands-Admin-Token", "admin-secret");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
