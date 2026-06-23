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
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;
import org.junit.jupiter.api.Test;

class CoreHttpRouteRegistrarTest {
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

    private static final class ServerFixture implements AutoCloseable {
        private final HttpServer server;
        private final CoreHttpRouteRegistrar registrar;
        private final HttpClient client = HttpClient.newHttpClient();

        private ServerFixture(HttpServer server, CoreHttpRouteRegistrar registrar) {
            this.server = server;
            this.registrar = registrar;
        }

        static ServerFixture start() throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CoreHttpRouteRegistrar registrar = new CoreHttpRouteRegistrar(
                new FixedWindowRateLimiter(Clock.systemUTC(), 100, 60_000L),
                new ApiTokenGuard("core-secret"),
                new MtlsHeaderGuard(false, "", ""),
                new IpAllowlist(""),
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
}
