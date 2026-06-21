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
import kr.lunaf.cloudislands.api.model.IslandLocation;
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

    @Test
    void jdkClientBuildsBankAndWarehousePayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/bank", exchange -> respond(exchange, requestBodies, "bank", "{\"balance\":\"10\"}"));
        server.createContext("/v1/islands/bank/deposit", exchange -> respond(exchange, requestBodies, "bankDeposit", "{\"accepted\":true,\"balance\":\"15\"}"));
        server.createContext("/v1/islands/bank/withdraw", exchange -> respond(exchange, requestBodies, "bankWithdraw", "{\"accepted\":true,\"balance\":\"8\"}"));
        server.createContext("/v1/islands/warehouse", exchange -> respond(exchange, requestBodies, "warehouse", "{\"items\":[]}"));
        server.createContext("/v1/islands/warehouse/deposit", exchange -> respond(exchange, requestBodies, "warehouseDeposit", "{\"accepted\":true,\"materialKey\":\"STONE\",\"amount\":12}"));
        server.createContext("/v1/islands/warehouse/withdraw", exchange -> respond(exchange, requestBodies, "warehouseWithdraw", "{\"accepted\":true,\"materialKey\":\"DIRT\",\"amount\":7}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.islandBank(islandId).join();
            client.depositIslandBank(islandId, actorUuid, "12.50").join();
            client.withdrawIslandBank(islandId, actorUuid, "4.25").join();
            client.islandWarehouse(islandId, 50).join();
            client.depositIslandWarehouse(islandId, actorUuid, "minecraft:stone", 12L).join();
            client.withdrawIslandWarehouse(islandId, actorUuid, "minecraft:dirt", 7L).join();

            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("bank"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"amount\":\"12.50\"}", requestBodies.get("bankDeposit"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"amount\":\"4.25\"}", requestBodies.get("bankWithdraw"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":50}", requestBodies.get("warehouse"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"materialKey\":\"minecraft:stone\",\"amount\":12}", requestBodies.get("warehouseDeposit"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"materialKey\":\"minecraft:dirt\",\"amount\":7}", requestBodies.get("warehouseWithdraw"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsWarpAndSettingsPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        IslandLocation location = new IslandLocation("world\"nether", 1.25D, 64.0D, -3.5D, 90.0F, 12.5F);
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/warps/set", exchange -> respond(exchange, requestBodies, "warpSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/warps/delete", exchange -> respond(exchange, requestBodies, "warpDelete", "{\"accepted\":true}"));
        server.createContext("/v1/islands/warps/access", exchange -> respond(exchange, requestBodies, "warpAccess", "{\"accepted\":true}"));
        server.createContext("/v1/islands/access", exchange -> respond(exchange, requestBodies, "publicAccess", "{\"accepted\":true}"));
        server.createContext("/v1/islands/lock", exchange -> respond(exchange, requestBodies, "locked", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.setIslandWarpResult(islandId, actorUuid, "spawn\"main", location, true, "market").join();
            client.deleteIslandWarpResult(islandId, actorUuid, "spawn\"main").join();
            client.setIslandWarpPublicAccessResult(islandId, actorUuid, "spawn\"main", false).join();
            client.setIslandPublicAccessResult(islandId, actorUuid, true).join();
            client.setIslandLockedResult(islandId, actorUuid, false).join();

            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\\\"main\",\"category\":\"market\",\"worldName\":\"world\\\"nether\",\"localX\":1.25,\"localY\":64.0,\"localZ\":-3.5,\"yaw\":90.0,\"pitch\":12.5,\"publicAccess\":true}", requestBodies.get("warpSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\\\"main\"}", requestBodies.get("warpDelete"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\\\"main\",\"publicAccess\":false}", requestBodies.get("warpAccess"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"publicAccess\":true}", requestBodies.get("publicAccess"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"locked\":false}", requestBodies.get("locked"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsBlockAndRankingPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/blocks/delta", exchange -> respond(exchange, requestBodies, "blockDelta", "{\"accepted\":true}"));
        server.createContext("/v1/islands/blocks", exchange -> respond(exchange, requestBodies, "blockDetails", "{\"blocks\":[],\"summary\":{}}"));
        server.createContext("/v1/islands/level/recalculate", exchange -> respond(exchange, requestBodies, "recalculate", "{\"level\":1}"));
        server.createContext("/v1/rankings/level", exchange -> respond(exchange, requestBodies, "rankLevel", "{\"rankings\":[]}"));
        server.createContext("/v1/rankings/worth", exchange -> respond(exchange, requestBodies, "rankWorth", "{\"rankings\":[]}"));
        server.createContext("/v1/rankings/reviews", exchange -> respond(exchange, requestBodies, "rankReviews", "{\"rankings\":[]}"));
        server.createContext("/v1/islands/public", exchange -> respond(exchange, requestBodies, "publicIslands", "{\"islands\":[]}"));
        server.createContext("/v1/admin/block-values", exchange -> respond(exchange, requestBodies, "blockValue", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.recordBlockDeltaResult(islandId, "minecraft:diamond\"block", 3L).join();
            client.islandBlockDetails(islandId, 25).join();
            client.recalculateIslandLevel(islandId, actorUuid).join();
            client.topIslandsByLevel(10).join();
            client.topIslandsByWorth(11).join();
            client.topIslandsByReviews(12).join();
            client.listPublicIslands(13).join();
            client.setBlockValueResult(actorUuid, "minecraft:emerald\"block", "100.50", 20L, 64L).join();

            assertEquals("{\"islandId\":\"" + islandId + "\",\"materialKey\":\"minecraft:diamond\\\"block\",\"delta\":3}", requestBodies.get("blockDelta"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":25}", requestBodies.get("blockDetails"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\"}", requestBodies.get("recalculate"));
            assertEquals("{\"limit\":10}", requestBodies.get("rankLevel"));
            assertEquals("{\"limit\":11}", requestBodies.get("rankWorth"));
            assertEquals("{\"limit\":12}", requestBodies.get("rankReviews"));
            assertEquals("{\"limit\":13}", requestBodies.get("publicIslands"));
            assertEquals("{\"actorUuid\":\"" + actorUuid + "\",\"materialKey\":\"minecraft:emerald\\\"block\",\"worth\":\"100.50\",\"levelPoints\":20,\"limit\":64}", requestBodies.get("blockValue"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsPublicWarpAndReviewPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/public-warps", exchange -> respond(exchange, requestBodies, "publicWarps", "{\"warps\":[]}"));
        server.createContext("/v1/islands/reviews", exchange -> respond(exchange, requestBodies, "reviews", "{\"reviews\":[]}"));
        server.createContext("/v1/islands/reviews/set", exchange -> respond(exchange, requestBodies, "reviewSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/reviews/delete", exchange -> respond(exchange, requestBodies, "reviewDelete", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.listPublicWarps(10).join();
            assertEquals("{\"limit\":10}", requestBodies.get("publicWarps"));
            client.listPublicWarps(11, "market\"zone", "spawn\"main").join();
            client.listIslandReviews(islandId, 12).join();
            client.setIslandReview(islandId, reviewerUuid, 5, "nice \"base\"").join();
            client.deleteIslandReview(islandId, reviewerUuid).join();

            assertEquals("{\"limit\":11,\"category\":\"market\\\"zone\",\"query\":\"spawn\\\"main\"}", requestBodies.get("publicWarps"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":12}", requestBodies.get("reviews"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reviewerUuid\":\"" + reviewerUuid + "\",\"rating\":5,\"comment\":\"nice \\\"base\\\"\"}", requestBodies.get("reviewSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reviewerUuid\":\"" + reviewerUuid + "\"}", requestBodies.get("reviewDelete"));
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, ConcurrentMap<String, String> requestBodies, String key, String responseBody) throws java.io.IOException {
        requestBodies.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
