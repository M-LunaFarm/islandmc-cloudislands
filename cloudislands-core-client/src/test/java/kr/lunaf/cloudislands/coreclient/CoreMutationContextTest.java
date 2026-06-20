package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class CoreMutationContextTest {
    @Test
    void jdkClientPropagatesMutationRequestIdIdempotencyKeyAndAuditAction() throws Exception {
        ConcurrentMap<String, List<String>> headers = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/reset", exchange -> {
            headers.put(CoreMutationContext.REQUEST_ID_HEADER, exchange.getRequestHeaders().get(CoreMutationContext.REQUEST_ID_HEADER));
            headers.put(CoreMutationContext.IDEMPOTENCY_KEY_HEADER, exchange.getRequestHeaders().get(CoreMutationContext.IDEMPOTENCY_KEY_HEADER));
            headers.put(CoreMutationContext.AUDIT_ACTION_HEADER, exchange.getRequestHeaders().get(CoreMutationContext.AUDIT_ACTION_HEADER));
            byte[] body = "{\"accepted\":true,\"code\":\"OK\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));
            CoreMutationMetadata metadata = new CoreMutationMetadata("req-1", "idem-1", "island.reset");

            CoreMutationContext.with(metadata, () -> client.resetIslandResult(UUID.randomUUID(), UUID.randomUUID(), "test")).join();

            assertEquals(List.of("req-1"), headers.get(CoreMutationContext.REQUEST_ID_HEADER));
            assertEquals(List.of("idem-1"), headers.get(CoreMutationContext.IDEMPOTENCY_KEY_HEADER));
            assertEquals(List.of("island.reset"), headers.get(CoreMutationContext.AUDIT_ACTION_HEADER));
        } finally {
            server.stop(0);
        }
    }
}
