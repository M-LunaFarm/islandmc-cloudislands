package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
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

    @Test
    void jdkClientParsesLifecycleResultsWithStructuredJson() throws Exception {
        UUID islandId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/", exchange -> {
            byte[] body = ("{\"accepted\" : false,\"code\" : \"DELETE_DENIED\",\"islandId\" : \"" + islandId + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/islands", exchange -> {
            byte[] body = "{\"accepted\" : true,\"code\" : \"CREATED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            var created = client.createIsland(UUID.randomUUID(), "default").join();
            var deleted = client.deleteIsland(UUID.randomUUID(), islandId).join();

            assertTrue(created.accepted());
            assertEquals("CREATED", created.code());
            assertFalse(deleted.accepted());
            assertEquals("DELETE_DENIED", deleted.code());
            assertEquals(islandId, deleted.islandId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientParsesRouteTicketResultsWithStructuredJson() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/routes/home", exchange -> {
            requestBodies.put("home", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("""
                {
                  "ticketId" : "%s",
                  "playerUuid" : "%s",
                  "action" : "HOME",
                  "islandId" : "%s",
                  "targetNode" : "node-a",
                  "targetWorld" : "world",
                  "state" : "READY",
                  "expiresAt" : "2026-06-21T00:00:30Z",
                  "nonce" : "nonce-a"
                }
                """.formatted(ticketId, playerUuid, islandId)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/routes/visit", exchange -> {
            requestBodies.put("visit", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"accepted\" : false,\"code\" : \"NO_ROUTE\",\"message\" : \"no route\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            var ticket = client.createHomeTicket(playerUuid, "spawn\"main").join();
            CompletionException failure = assertThrows(CompletionException.class, () -> client.createVisitTicket(playerUuid, islandId).join());
            CoreApiException apiFailure = assertInstanceOf(CoreApiException.class, failure.getCause());

            assertEquals(ticketId, ticket.ticketId());
            assertEquals(playerUuid, ticket.playerUuid());
            assertEquals("node-a", ticket.targetNode());
            assertEquals("NO_ROUTE", apiFailure.code());
            assertEquals("no route", apiFailure.getMessage());
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"homeName\":\"spawn\\\"main\"}", requestBodies.get("home"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}", requestBodies.get("visit"));
        } finally {
            server.stop(0);
        }
    }
}
