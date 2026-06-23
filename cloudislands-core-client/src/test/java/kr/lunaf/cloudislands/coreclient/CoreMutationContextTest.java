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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
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

            CoreMutationContext.with(metadata, () -> client.lifecycle().resetIsland(UUID.randomUUID(), UUID.randomUUID(), "test")).join();

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
    void jdkClientBuildsLifecycleAndInfoPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands", exchange -> respond(exchange, requestBodies, "create", "{\"accepted\":true,\"code\":\"CREATED\"}"));
        server.createContext("/v1/islands/reset", exchange -> respond(exchange, requestBodies, "reset", "{\"accepted\":true}"));
        server.createContext("/v1/islands/info", exchange -> respond(exchange, requestBodies, "info", "{\"islandId\":\"" + islandId + "\"}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.createIsland(playerUuid, "template\"one").join();
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"templateId\":\"template\\\"one\"}", requestBodies.get("create"));

            client.lifecycle().resetIsland(islandId, actorUuid, "reset \"reason\"").join();
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"reason\":\"reset \\\"reason\\\"\"}", requestBodies.get("reset"));

            client.islands().getIsland(islandId).join();
            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("info"));
            client.islands().getIslandByOwner(ownerUuid).join();
            assertEquals("{\"ownerUuid\":\"" + ownerUuid + "\"}", requestBodies.get("info"));
            client.islands().findIslandByName("island \"name\"").join();
            assertEquals("{\"name\":\"island \\\"name\\\"\"}", requestBodies.get("info"));
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
    void jdkClientParsesRouteSessionsWithStructuredJson() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/routes/session/find", exchange -> {
            requestBodies.put("find", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("""
                {
                  "playerUuid" : "%s",
                  "ticketId" : "%s",
                  "targetNode" : "node-\\"a",
                  "targetServerName" : "island-\\"a",
                  "nonce" : "nonce-\\"a",
                  "expiresAt" : "2026-06-21T00:00:30Z"
                }
                """.formatted(playerUuid, ticketId)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            var session = client.findRouteSession(playerUuid, "node\"a").join().orElseThrow();

            assertEquals(playerUuid, session.playerUuid());
            assertEquals(ticketId, session.ticketId());
            assertEquals("node-\"a", session.targetNode());
            assertEquals("island-\"a", session.targetServerName());
            assertEquals("nonce-\"a", session.nonce());
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"node\\\"a\"}", requestBodies.get("find"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsProgressionLimitSnapshotAndLogPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/upgrades", exchange -> respond(exchange, requestBodies, "upgrades", "{\"upgrades\":[]}"));
        server.createContext("/v1/islands/upgrades/purchase", exchange -> respond(exchange, requestBodies, "upgradePurchase", "{\"accepted\":true}"));
        server.createContext("/v1/islands/missions", exchange -> respond(exchange, requestBodies, "missions", "{\"missions\":[]}"));
        server.createContext("/v1/islands/missions/complete", exchange -> respond(exchange, requestBodies, "missionComplete", "{\"accepted\":true}"));
        server.createContext("/v1/islands/missions/progress", exchange -> respond(exchange, requestBodies, "missionProgress", "{\"accepted\":true}"));
        server.createContext("/v1/addons/missions/register", exchange -> respond(exchange, requestBodies, "missionRegister", "{\"accepted\":true}"));
        server.createContext("/v1/islands/limits", exchange -> respond(exchange, requestBodies, "limits", "{\"limits\":[]}"));
        server.createContext("/v1/islands/limits/set", exchange -> respond(exchange, requestBodies, "limitSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/chat", exchange -> respond(exchange, requestBodies, "chat", "{\"accepted\":true}"));
        server.createContext("/v1/islands/snapshots", exchange -> respond(exchange, requestBodies, "snapshots", "{\"snapshots\":[]}"));
        server.createContext("/v1/islands/snapshots/record", exchange -> respond(exchange, requestBodies, "snapshotRecord", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/save", exchange -> respond(exchange, requestBodies, "save", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/snapshot", exchange -> respond(exchange, requestBodies, "snapshot", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/restore", exchange -> respond(exchange, requestBodies, "restore", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/rollback", exchange -> respond(exchange, requestBodies, "rollback", "{\"accepted\":true}"));
        server.createContext("/v1/islands/logs", exchange -> respond(exchange, requestBodies, "logs", "{\"logs\":[]}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.progression().upgrades(islandId).join();
            client.progressionCommands().purchaseUpgrade(islandId, actorUuid, "generator\"speed").join();
            client.progression().missions(islandId, "MISSION\"DAILY").join();
            client.progressionCommands().completeMission(islandId, actorUuid, "starter\"mission", "CHALLENGE").join();
            client.progressionCommands().progressMission(islandId, actorUuid, "starter\"mission", "CHALLENGE", -5L).join();
            client.progressionCommands().registerMissionProvider("provider\"one", List.of(new MissionProviderDefinitionSnapshot(
                "provider\"one",
                "starter",
                "MISSION",
                "",
                1L,
                ""
            ))).join();
            client.environment().limits(islandId).join();
            client.environmentCommands().setLimit(islandId, actorUuid, "HOPPER\"LIMIT", 64L).join();
            client.communicationCommands().sendChat(islandId, actorUuid, "team\"chat", "hello \"team\"").join();
            client.snapshots().records(islandId, 15).join();
            client.snapshotCommands().recordSnapshot(islandId, 7L, "snapshots/base\"one.tar", "manual \"save\"", "abc\"123", 4096L, "node\"a").join();
            assertEquals("{\"islandId\":\"" + islandId + "\",\"snapshotNo\":7,\"storagePath\":\"snapshots/base\\\"one.tar\",\"reason\":\"manual \\\"save\\\"\",\"checksum\":\"abc\\\"123\",\"sizeBytes\":4096,\"nodeId\":\"node\\\"a\",\"fencingToken\":0}", requestBodies.get("snapshotRecord"));
            client.snapshotCommands().recordSnapshot(islandId, 8L, "snapshots/base\"two.tar", "auto \"save\"", "def\"456", 8192L, "node\"b", 123L).join();
            client.lifecycle().saveIsland(islandId, "save \"now\"").join();
            client.lifecycle().snapshotIsland(islandId, "snapshot \"now\"").join();
            client.lifecycle().restoreIslandSnapshot(islandId, 7L).join();
            client.lifecycle().rollbackIslandSnapshot(islandId, 6L).join();
            client.communication().records(islandId, 25).join();

            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("upgrades"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"upgradeKey\":\"generator\\\"speed\"}", requestBodies.get("upgradePurchase"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"kind\":\"MISSION\\\"DAILY\"}", requestBodies.get("missions"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"missionKey\":\"starter\\\"mission\",\"kind\":\"CHALLENGE\"}", requestBodies.get("missionComplete"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"missionKey\":\"starter\\\"mission\",\"kind\":\"CHALLENGE\",\"amount\":0}", requestBodies.get("missionProgress"));
            assertEquals("{\"providerId\":\"provider\\\"one\",\"missions\":[{\"missionKey\":\"starter\",\"kind\":\"MISSION\",\"title\":\"starter\",\"goal\":1,\"reward\":\"\",\"enabled\":true}]}", requestBodies.get("missionRegister"));
            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("limits"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"limitKey\":\"HOPPER\\\"LIMIT\",\"value\":64}", requestBodies.get("limitSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"channel\":\"TEAM\\\"CHAT\",\"message\":\"hello \\\"team\\\"\"}", requestBodies.get("chat"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":15}", requestBodies.get("snapshots"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"snapshotNo\":8,\"storagePath\":\"snapshots/base\\\"two.tar\",\"reason\":\"auto \\\"save\\\"\",\"checksum\":\"def\\\"456\",\"sizeBytes\":8192,\"nodeId\":\"node\\\"b\",\"fencingToken\":123}", requestBodies.get("snapshotRecord"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reason\":\"save \\\"now\\\"\"}", requestBodies.get("save"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reason\":\"snapshot \\\"now\\\"\"}", requestBodies.get("snapshot"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"snapshotNo\":7}", requestBodies.get("restore"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"snapshotNo\":6}", requestBodies.get("rollback"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":25}", requestBodies.get("logs"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsAdminNodeRouteEventAndIslandPayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID lookupUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/admin/nodes/info", exchange -> respond(exchange, requestBodies, "nodeInfo", "{\"nodeId\":\"node-a\"}"));
        server.createContext("/v1/admin/nodes/islands", exchange -> respond(exchange, requestBodies, "nodeIslands", "{\"islands\":[]}"));
        server.createContext("/v1/admin/nodes/drain", exchange -> respond(exchange, requestBodies, "nodeDrain", "{\"accepted\":true}"));
        server.createContext("/v1/admin/nodes/undrain", exchange -> respond(exchange, requestBodies, "nodeUndrain", "{\"accepted\":true}"));
        server.createContext("/v1/admin/nodes/sweep", exchange -> respond(exchange, requestBodies, "nodeSweep", "{\"accepted\":true}"));
        server.createContext("/v1/admin/nodes/kickall", exchange -> respond(exchange, requestBodies, "nodeKickAll", "{\"accepted\":true}"));
        server.createContext("/v1/admin/nodes/shutdown-safe", exchange -> respond(exchange, requestBodies, "nodeShutdown", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/activate", exchange -> respond(exchange, requestBodies, "islandActivate", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/deactivate", exchange -> respond(exchange, requestBodies, "islandDeactivate", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/migrate", exchange -> respond(exchange, requestBodies, "islandMigrate", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/" + islandId + "/quarantine", exchange -> respond(exchange, requestBodies, "islandQuarantine", "{\"accepted\":true}"));
        server.createContext("/v1/admin/islands/info", exchange -> respond(exchange, requestBodies, "islandInfo", "{\"islandId\":\"" + islandId + "\"}"));
        server.createContext("/v1/admin/islands/where", exchange -> respond(exchange, requestBodies, "islandWhere", "{\"nodeId\":\"node-a\"}"));
        server.createContext("/v1/admin/islands/tp", exchange -> respond(exchange, requestBodies, "islandTp", "{\"ticketId\":\"" + ticketId + "\",\"playerUuid\":\"" + playerUuid + "\",\"action\":\"VISIT\",\"islandId\":\"" + islandId + "\",\"targetNode\":\"node-a\",\"targetWorld\":\"world\",\"state\":\"READY\",\"expiresAt\":\"2026-06-21T00:00:30Z\",\"nonce\":\"nonce-a\"}"));
        server.createContext("/v1/admin/islands/" + islandId + "/repair", exchange -> respond(exchange, requestBodies, "islandRepair", "{\"accepted\":true}"));
        server.createContext("/v1/admin/routes/debug", exchange -> respond(exchange, requestBodies, "routesDebug", "{\"tickets\":[]}"));
        server.createContext("/v1/admin/routes/ticket", exchange -> respond(exchange, requestBodies, "routeTicket", "{\"ticket\":null}"));
        server.createContext("/v1/admin/routes/clear", exchange -> respond(exchange, requestBodies, "routeClear", "{\"accepted\":true}"));
        server.createContext("/v1/events", exchange -> respond(exchange, requestBodies, "events", "{\"events\":[]}"));
        server.createContext("/v1/admin/audit/list", exchange -> respond(exchange, requestBodies, "audit", "{\"entries\":[]}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.adminNodes().nodeInfo("node\"a").join();
            client.adminNodes().nodeIslandRuntimes("node\"a", 500).join();
            client.adminNodeCommands().drainNode("node\"a").join();
            client.adminNodeCommands().undrainNode("node\"a").join();
            client.adminNodeCommands().sweepNode("node\"a").join();
            client.adminNodeCommands().kickAllNode("node\"a", "kick \"all\"").join();
            client.adminNodeCommands().shutdownNodeSafely("node\"a", "shutdown \"all\"").join();
            client.lifecycle().activateIsland(islandId).join();
            client.lifecycle().deactivateIsland(islandId).join();
            client.lifecycle().migrateIsland(islandId, "target\"node").join();
            client.lifecycle().quarantineIsland(islandId, "bad \"state\"").join();
            client.adminIslands().info(lookupUuid).join();
            client.adminIslands().runtime(islandId).join();
            client.adminIslandTeleport(playerUuid, islandId).join();
            client.lifecycle().repairIsland(islandId, "repair \"now\"").join();
            client.adminRoutes().debug(playerUuid).join();
            client.adminRoutes().ticket(ticketId).join();
            assertEquals("{\"ticketId\":\"" + ticketId + "\"}", requestBodies.get("routeTicket"));
            client.adminRoutes().ticketForPlayer(playerUuid).join();
            client.adminRoutes().clear(playerUuid, ticketId, "").join();
            client.adminEvents().list(100).join();
            assertEquals("{\"limit\":100}", requestBodies.get("events"));
            client.adminEvents().list(5000).join();
            assertEquals("{\"limit\":4096}", requestBodies.get("events"));
            client.adminEvents().listSince(-5L, 0).join();
            client.adminAudit().list(1000).join();
            assertEquals("{\"limit\":500}", requestBodies.get("audit"));

            assertEquals("{\"nodeId\":\"node\\\"a\"}", requestBodies.get("nodeInfo"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"limit\":100}", requestBodies.get("nodeIslands"));
            assertEquals("{\"nodeId\":\"node\\\"a\"}", requestBodies.get("nodeDrain"));
            assertEquals("{\"nodeId\":\"node\\\"a\"}", requestBodies.get("nodeUndrain"));
            assertEquals("{\"nodeId\":\"node\\\"a\"}", requestBodies.get("nodeSweep"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"reason\":\"kick \\\"all\\\"\"}", requestBodies.get("nodeKickAll"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"reason\":\"shutdown \\\"all\\\"\"}", requestBodies.get("nodeShutdown"));
            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("islandActivate"));
            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("islandDeactivate"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"targetNode\":\"target\\\"node\"}", requestBodies.get("islandMigrate"));
            assertEquals("{\"reason\":\"bad \\\"state\\\"\"}", requestBodies.get("islandQuarantine"));
            assertEquals("{\"lookupUuid\":\"" + lookupUuid + "\"}", requestBodies.get("islandInfo"));
            assertEquals("{\"islandId\":\"" + islandId + "\"}", requestBodies.get("islandWhere"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}", requestBodies.get("islandTp"));
            assertEquals("{\"reason\":\"repair \\\"now\\\"\"}", requestBodies.get("islandRepair"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("routesDebug"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("routeTicket"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"ticketId\":\"" + ticketId + "\",\"reason\":\"MANUAL_CLEAR\"}", requestBodies.get("routeClear"));
            assertEquals("{\"sinceSeq\":0,\"limit\":1}", requestBodies.get("events"));
            assertEquals("{\"limit\":500}", requestBodies.get("audit"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsPlayerTemplateJobAndHeartbeatPayloadsWithStructuredHelper() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Map<String, String> jobPayload = new LinkedHashMap<>();
        jobPayload.put("path", "jobs/one\"two");
        jobPayload.put("note", "done");
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/admin/players/info", exchange -> respond(exchange, requestBodies, "playerInfo", "{\"player\":{}}"));
        server.createContext("/v1/players/info", exchange -> respond(exchange, requestBodies, "playerInfoByName", "{\"player\":{}}"));
        server.createContext("/v1/players/touch", exchange -> respond(exchange, requestBodies, "playerTouch", "{\"accepted\":true}"));
        server.createContext("/v1/players/locale", exchange -> respond(exchange, requestBodies, "playerLocale", "{\"accepted\":true}"));
        server.createContext("/v1/admin/players/setisland", exchange -> respond(exchange, requestBodies, "playerSetIsland", "{\"accepted\":true}"));
        server.createContext("/v1/admin/players/clearisland", exchange -> respond(exchange, requestBodies, "playerClearIsland", "{\"accepted\":true}"));
        server.createContext("/v1/admin/templates/upsert", exchange -> respond(exchange, requestBodies, "templateUpsert", "{\"accepted\":true}"));
        server.createContext("/v1/admin/templates/enable", exchange -> respond(exchange, requestBodies, "templateEnable", "{\"accepted\":true}"));
        server.createContext("/v1/admin/templates/disable", exchange -> respond(exchange, requestBodies, "templateDisable", "{\"accepted\":true}"));
        server.createContext("/v1/jobs/claim", exchange -> respond(exchange, requestBodies, "jobClaim", "[]"));
        server.createContext("/v1/admin/jobs/retry", exchange -> respond(exchange, requestBodies, "jobRetry", "{\"accepted\":true}"));
        server.createContext("/v1/admin/jobs/cancel", exchange -> respond(exchange, requestBodies, "jobCancel", "{\"accepted\":true}"));
        server.createContext("/v1/admin/jobs/recover", exchange -> respond(exchange, requestBodies, "jobRecover", "{\"accepted\":true}"));
        server.createContext("/v1/jobs/complete", exchange -> respond(exchange, requestBodies, "jobComplete", "{\"accepted\":true}"));
        server.createContext("/v1/jobs/fail", exchange -> respond(exchange, requestBodies, "jobFail", "{\"accepted\":true}"));
        server.createContext("/v1/nodes/heartbeat", exchange -> respond(exchange, requestBodies, "heartbeat", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.playerProfiles().profile(playerUuid).join();
            client.playerProfiles().findByName("Player \"One\"").join();
            client.playerProfileCommands().touch(playerUuid, "Player \"One\"").join();
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"lastName\":\"Player \\\"One\\\"\"}", requestBodies.get("playerTouch"));
            client.playerProfileCommands().touch(playerUuid, "Player \"One\"", "ko\"KR").join();
            client.playerProfileCommands().setLocale(playerUuid, "en\"US").join();
            client.playerProfileCommands().setPrimaryIsland(playerUuid, islandId).join();
            client.playerProfileCommands().clearPrimaryIsland(playerUuid).join();
            client.templateCommands().upsert("template\"one", "Template \"One\"", true, "1.21\"11").join();
            client.templateCommands().enable("template\"one").join();
            client.templateCommands().disable("template\"one").join();
            client.claimJobs("node\"a", List.of(IslandJobType.CREATE_ISLAND, IslandJobType.SAVE_ISLAND), 3).join();
            client.jobCommands().retry(jobId).join();
            client.jobCommands().cancel(jobId).join();
            client.jobCommands().recover("node\"a", 50L, 2).join();
            client.runtimeCommands().completeJob("node\"a", jobId, jobPayload).join();
            client.runtimeCommands().failJob("node\"a", jobId, "failed \"hard\"").join();
            client.runtimeCommands().publishHeartbeat(new NodeHeartbeatRequest(
                NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
                "node\"a",
                "default\"pool",
                "island-a",
                "1.0\"rc",
                NodeState.READY,
                10,
                20,
                30,
                2,
                5,
                15,
                19.5D,
                1,
                4,
                0.25D,
                512L,
                2048L,
                3,
                true,
                "default,nether\"template"
            )).join();

            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("playerInfo"));
            assertEquals("{\"lastName\":\"Player \\\"One\\\"\"}", requestBodies.get("playerInfoByName"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"lastName\":\"Player \\\"One\\\"\",\"locale\":\"ko\\\"KR\"}", requestBodies.get("playerTouch"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"locale\":\"en\\\"US\"}", requestBodies.get("playerLocale"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}", requestBodies.get("playerSetIsland"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("playerClearIsland"));
            assertEquals("{\"templateId\":\"template\\\"one\",\"displayName\":\"Template \\\"One\\\"\",\"enabled\":true,\"minNodeVersion\":\"1.21\\\"11\"}", requestBodies.get("templateUpsert"));
            assertEquals("{\"templateId\":\"template\\\"one\"}", requestBodies.get("templateEnable"));
            assertEquals("{\"templateId\":\"template\\\"one\"}", requestBodies.get("templateDisable"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"supportedTypes\":\"CREATE_ISLAND,SAVE_ISLAND\",\"maxJobs\":3}", requestBodies.get("jobClaim"));
            assertEquals("{\"jobId\":\"" + jobId + "\"}", requestBodies.get("jobRetry"));
            assertEquals("{\"jobId\":\"" + jobId + "\"}", requestBodies.get("jobCancel"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"minIdleMillis\":50,\"maxJobs\":2}", requestBodies.get("jobRecover"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"jobId\":\"" + jobId + "\",\"claimLease\":{},\"payload\":{\"path\":\"jobs/one\\\"two\",\"note\":\"done\"}}", requestBodies.get("jobComplete"));
            assertEquals("{\"nodeId\":\"node\\\"a\",\"jobId\":\"" + jobId + "\",\"claimLease\":{},\"error\":\"failed \\\"hard\\\"\"}", requestBodies.get("jobFail"));
            assertEquals("{\"protocolVersion\":1,\"nodeId\":\"node\\\"a\",\"pool\":\"default\\\"pool\",\"velocityServerName\":\"island-a\",\"nodeVersion\":\"1.0\\\"rc\",\"state\":\"READY\",\"players\":10,\"softPlayerCap\":20,\"hardPlayerCap\":30,\"reservedSlots\":2,\"activeIslands\":5,\"maxActiveIslands\":15,\"mspt\":19.5,\"activationQueue\":1,\"maxActivationQueue\":4,\"chunkLoadPressure\":0.25,\"heapUsedMb\":512,\"heapMaxMb\":2048,\"recentFailurePenalty\":3,\"storageAvailable\":true,\"supportedTemplates\":\"default,nether\\\"template\"}", requestBodies.get("heartbeat"));
            JobClaimLease lease = new JobClaimLease(jobId, "1730000000000-0", "node\"a", "token\"1", 2L, Instant.parse("2026-06-23T01:02:03Z"), 1);
            client.runtimeCommands().completeJob("node\"a", jobId, lease, Map.of()).join();
            assertEquals("{\"nodeId\":\"node\\\"a\",\"jobId\":\"" + jobId + "\",\"claimLease\":{\"jobId\":\"" + jobId + "\",\"streamId\":\"1730000000000-0\",\"claimedByNode\":\"node\\\"a\",\"claimToken\":\"token\\\"1\",\"claimEpoch\":2,\"leaseExpiresAt\":\"2026-06-23T01:02:03Z\",\"attempt\":1},\"payload\":{}}", requestBodies.get("jobComplete"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsGlobalAddonStatePayloadsWithStructuredHelper() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("alpha", "one\"1");
        values.put("beta", "two");
        Map<String, String> tableValues = new LinkedHashMap<>();
        tableValues.put("row", "value\"1");
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        tables.put("table\"one", tableValues);
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/addons/state", exchange -> respond(exchange, requestBodies, "state", "{\"values\":{}}"));
        server.createContext("/v1/addons/state/set", exchange -> respond(exchange, requestBodies, "set", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/bulk", exchange -> respond(exchange, requestBodies, "bulk", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/save", exchange -> respond(exchange, requestBodies, "save", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table-key-value/bulk-save", exchange -> respond(exchange, requestBodies, "legacyBulkSave", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/key-value/bulk-save", exchange -> respond(exchange, requestBodies, "bulkSave", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/key-value/bulk/save", exchange -> respond(exchange, requestBodies, "bulkSaveAlias", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/key-value/bulk", exchange -> respond(exchange, requestBodies, "bulkAlias", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/bulk", exchange -> respond(exchange, requestBodies, "tableBulk", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/bulk-set", exchange -> respond(exchange, requestBodies, "tableBulkSet", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/load", exchange -> respond(exchange, requestBodies, "tableLoadAlias", "{\"values\":{}}"));
        server.createContext("/v1/addons/state/table/key-value/bulk-load", exchange -> respond(exchange, requestBodies, "tableLoad", "{\"values\":{}}"));
        server.createContext("/v1/addons/state/table/replace", exchange -> respond(exchange, requestBodies, "tableReplace", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/table/clear", exchange -> respond(exchange, requestBodies, "tableClear", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/remove", exchange -> respond(exchange, requestBodies, "remove", "{\"accepted\":true}"));
        server.createContext("/v1/addons/state/clear", exchange -> respond(exchange, requestBodies, "clear", "{\"accepted\":true}"));
        server.start();
        try {
            AddonStateClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).addonStates();

            client.state("addon\"one").join();
            client.putState("addon\"one", values).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("bulk"));
            client.saveState("addon\"one", values, tables).join();
            client.tableKeyValueBulkSaveState("addon\"one", values, tables).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"},\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("bulkSave"));
            client.tableBulkState("addon\"one", tables).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("tableBulk"));
            client.tableKeyValueBulkLoadState("addon\"one", "table\"one").join();
            client.putTableState("addon\"one", "table\"one", values).join();
            client.replaceTableState("addon\"one", "table\"one", values).join();
            client.clearTableState("addon\"one", "table\"one").join();
            client.removeState("addon\"one", "key\"one").join();
            client.clearState("addon\"one").join();

            assertEquals("{\"addonId\":\"addon\\\"one\"}", requestBodies.get("state"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"},\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("save"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"},\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("bulkSave"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"table\":\"table\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("tableBulk"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"table\":\"table\\\"one\"}", requestBodies.get("tableLoad"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"table\":\"table\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("tableReplace"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"table\":\"table\\\"one\"}", requestBodies.get("tableClear"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"key\":\"key\\\"one\"}", requestBodies.get("remove"));
            assertEquals("{\"addonId\":\"addon\\\"one\"}", requestBodies.get("clear"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsIslandAddonStatePayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("alpha", "one\"1");
        values.put("beta", "two");
        Map<String, String> tableValues = new LinkedHashMap<>();
        tableValues.put("row", "value\"1");
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        tables.put("table\"one", tableValues);
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/addons/islands/state", exchange -> respond(exchange, requestBodies, "state", "{\"values\":{}}"));
        server.createContext("/v1/addons/islands/state/set", exchange -> respond(exchange, requestBodies, "set", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/bulk", exchange -> respond(exchange, requestBodies, "bulk", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/save", exchange -> respond(exchange, requestBodies, "save", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table-key-value/bulk-save", exchange -> respond(exchange, requestBodies, "legacyBulkSave", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/key-value/bulk-save", exchange -> respond(exchange, requestBodies, "bulkSave", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/key-value/bulk/save", exchange -> respond(exchange, requestBodies, "bulkSaveAlias", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/key-value/bulk", exchange -> respond(exchange, requestBodies, "bulkAlias", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/bulk", exchange -> respond(exchange, requestBodies, "tableBulk", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/bulk-set", exchange -> respond(exchange, requestBodies, "tableBulkSet", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/load", exchange -> respond(exchange, requestBodies, "tableLoadAlias", "{\"values\":{}}"));
        server.createContext("/v1/addons/islands/state/table/key-value/bulk-load", exchange -> respond(exchange, requestBodies, "tableLoad", "{\"values\":{}}"));
        server.createContext("/v1/addons/islands/state/table/replace", exchange -> respond(exchange, requestBodies, "tableReplace", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/table/clear", exchange -> respond(exchange, requestBodies, "tableClear", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/remove", exchange -> respond(exchange, requestBodies, "remove", "{\"accepted\":true}"));
        server.createContext("/v1/addons/islands/state/clear", exchange -> respond(exchange, requestBodies, "clear", "{\"accepted\":true}"));
        server.start();
        try {
            AddonStateClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).addonStates();

            client.islandState("addon\"one", islandId).join();
            client.putIslandState("addon\"one", islandId, values).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("bulk"));
            client.saveIslandState("addon\"one", islandId, values, tables).join();
            client.tableKeyValueBulkSaveIslandState("addon\"one", islandId, values, tables).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"},\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("bulkSave"));
            client.tableBulkIslandState("addon\"one", islandId, tables).join();
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("tableBulk"));
            client.tableKeyValueBulkLoadIslandState("addon\"one", islandId, "table\"one").join();
            client.putIslandTableState("addon\"one", islandId, "table\"one", values).join();
            client.replaceIslandTableState("addon\"one", islandId, "table\"one", values).join();
            client.clearIslandTableState("addon\"one", islandId, "table\"one").join();
            client.removeIslandState("addon\"one", islandId, "key\"one").join();
            client.clearIslandState("addon\"one", islandId).join();

            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\"}", requestBodies.get("state"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"},\"tables\":{\"table\\\"one\":{\"row\":\"value\\\"1\"}}}", requestBodies.get("save"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"table\":\"table\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("tableBulk"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"table\":\"table\\\"one\"}", requestBodies.get("tableLoad"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"table\":\"table\\\"one\",\"values\":{\"alpha\":\"one\\\"1\",\"beta\":\"two\"}}", requestBodies.get("tableReplace"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"table\":\"table\\\"one\"}", requestBodies.get("tableClear"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\",\"key\":\"key\\\"one\"}", requestBodies.get("remove"));
            assertEquals("{\"addonId\":\"addon\\\"one\",\"islandId\":\"" + islandId + "\"}", requestBodies.get("clear"));
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

            client.bank().snapshot(islandId).join();
            client.bankCommands().depositSnapshot(islandId, actorUuid, "12.50").join();
            client.bankCommands().withdrawSnapshot(islandId, actorUuid, "4.25").join();
            client.warehouse().listItems(islandId, 50).join();
            client.warehouseCommands().deposit(islandId, actorUuid, "minecraft:stone", 12L).join();
            client.warehouseCommands().withdraw(islandId, actorUuid, "minecraft:dirt", 7L).join();

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

            client.homeWarpCommands().setWarp(islandId, actorUuid, "spawn\"main", location, true, "market").join();
            client.homeWarpCommands().deleteWarp(islandId, actorUuid, "spawn\"main").join();
            client.homeWarpCommands().setWarpPublicAccess(islandId, actorUuid, "spawn\"main", false).join();
            client.settingsCommands().setPublicAccess(islandId, actorUuid, true).join();
            client.settingsCommands().setLocked(islandId, actorUuid, false).join();

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
        server.createContext("/v1/islands/blocks/replace", exchange -> respond(exchange, requestBodies, "blockReplace", "{\"accepted\":true}"));
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
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("minecraft:stone\"block", 3L);
            counts.put("ignored", 0L);

            client.runtimeCommands().recordBlockDelta(islandId, "minecraft:diamond\"block", 3L).join();
            client.runtimeCommands().replaceBlockCounts(islandId, counts).join();
            client.progression().blockDetails(islandId, 25).join();
            client.progressionCommands().recalculateLevel(islandId, actorUuid).join();
            client.progression().topLevel(10).join();
            client.progression().topWorth(11).join();
            client.progression().topReviews(12).join();
            client.navigation().publicIslands(13).join();
            client.blockValueCommands().set(actorUuid, "minecraft:emerald\"block", "100.50", 20L, 64L).join();

            assertEquals("{\"islandId\":\"" + islandId + "\",\"materialKey\":\"minecraft:diamond\\\"block\",\"delta\":3}", requestBodies.get("blockDelta"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"counts\":{\"minecraft:stone\\\"block\":3}}", requestBodies.get("blockReplace"));
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

            client.homeWarps().publicWarpSnapshots(10, "", "").join();
            assertEquals("{\"limit\":10}", requestBodies.get("publicWarps"));
            client.homeWarps().publicWarpSnapshots(11, "market\"zone", "spawn\"main").join();
            client.navigation().listReviews(islandId, 12).join();
            client.navigationCommands().setReview(islandId, reviewerUuid, 5, "nice \"base\"").join();
            client.navigationCommands().deleteReview(islandId, reviewerUuid).join();

            assertEquals("{\"limit\":11,\"category\":\"market\\\"zone\",\"query\":\"spawn\\\"main\"}", requestBodies.get("publicWarps"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":12}", requestBodies.get("reviews"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reviewerUuid\":\"" + reviewerUuid + "\",\"rating\":5,\"comment\":\"nice \\\"base\\\"\"}", requestBodies.get("reviewSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"reviewerUuid\":\"" + reviewerUuid + "\"}", requestBodies.get("reviewDelete"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsPermissionAndRolePayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/permissions/set", exchange -> respond(exchange, requestBodies, "permissionSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/permissions/overrides/set", exchange -> respond(exchange, requestBodies, "permissionOverride", "{\"accepted\":true}"));
        server.createContext("/v1/islands/roles/upsert", exchange -> respond(exchange, requestBodies, "roleUpsert", "{\"accepted\":true}"));
        server.createContext("/v1/islands/roles/reset", exchange -> respond(exchange, requestBodies, "roleReset", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.permissions().updatePermissions(new UpdatePermissionsRequest(
                islandId,
                actorUuid,
                java.util.List.of(new UpdatePermissionsRequest.Change("builder-role", IslandPermission.BUILD, true, "v\"2"))
            )).join();
            client.permissions().setPermissionOverride(islandId, actorUuid, playerUuid, IslandPermission.BREAK, false).join();
            client.permissions().upsertRole(islandId, actorUuid, "builder-role", 42, "Builder \"Role\"").join();
            client.permissions().resetRole(islandId, actorUuid, "builder-role").join();

            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER_ROLE\",\"roleKey\":\"BUILDER_ROLE\",\"permission\":\"BUILD\",\"allowed\":true,\"expectedVersion\":\"v\\\"2\"}", requestBodies.get("permissionSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"permission\":\"BREAK\",\"allowed\":false}", requestBodies.get("permissionOverride"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER_ROLE\",\"roleKey\":\"BUILDER_ROLE\",\"weight\":42,\"displayName\":\"Builder \\\"Role\\\"\"}", requestBodies.get("roleUpsert"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER_ROLE\",\"roleKey\":\"BUILDER_ROLE\"}", requestBodies.get("roleReset"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientBuildsMemberEnvironmentAndHomePayloadsWithStructuredHelper() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        IslandLocation location = new IslandLocation("world\"home", 10.5D, 65.0D, -2.25D, 180.0F, 15.0F);
        ConcurrentMap<String, String> requestBodies = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/islands/name", exchange -> respond(exchange, requestBodies, "name", "{\"accepted\":true}"));
        server.createContext("/v1/islands/members/set", exchange -> respond(exchange, requestBodies, "memberSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/members/trust-temporary", exchange -> respond(exchange, requestBodies, "memberTrust", "{\"accepted\":true}"));
        server.createContext("/v1/islands/transfer", exchange -> respond(exchange, requestBodies, "transfer", "{\"accepted\":true}"));
        server.createContext("/v1/islands/members/remove", exchange -> respond(exchange, requestBodies, "memberRemove", "{\"accepted\":true}"));
        server.createContext("/v1/islands/invites", exchange -> respond(exchange, requestBodies, "inviteCreate", "{\"inviteId\":\"" + inviteId + "\"}"));
        server.createContext("/v1/players/invites", exchange -> respond(exchange, requestBodies, "inviteList", "{\"invites\":[]}"));
        server.createContext("/v1/players/islands", exchange -> respond(exchange, requestBodies, "playerIslands", "{\"islands\":[]}"));
        server.createContext("/v1/islands/invites/accept", exchange -> respond(exchange, requestBodies, "inviteAccept", "{\"accepted\":true}"));
        server.createContext("/v1/islands/invites/decline", exchange -> respond(exchange, requestBodies, "inviteDecline", "{\"accepted\":true}"));
        server.createContext("/v1/islands/bans/set", exchange -> respond(exchange, requestBodies, "banSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/bans/remove", exchange -> respond(exchange, requestBodies, "banRemove", "{\"accepted\":true}"));
        server.createContext("/v1/islands/visitors/kick", exchange -> respond(exchange, requestBodies, "visitorKick", "{\"accepted\":true}"));
        server.createContext("/v1/islands/visitors/stats", exchange -> respond(exchange, requestBodies, "visitorStats", "{\"visitors\":[]}"));
        server.createContext("/v1/islands/flags/set", exchange -> respond(exchange, requestBodies, "flagSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/biome/set", exchange -> respond(exchange, requestBodies, "biomeSet", "{\"accepted\":true}"));
        server.createContext("/v1/islands/homes/set", exchange -> respond(exchange, requestBodies, "homeSet", "{\"accepted\":true}"));
        server.start();
        try {
            JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2));

            client.settingsCommands().setName(islandId, actorUuid, "Base \"One\"").join();
            client.memberCommands().setRole(islandId, actorUuid, playerUuid, "trusted-member").join();
            client.memberCommands().trustTemporarily(islandId, actorUuid, playerUuid, 30L).join();
            client.memberCommands().transferOwnership(islandId, actorUuid, playerUuid).join();
            client.memberCommands().removeMember(islandId, actorUuid, playerUuid).join();
            client.memberCommands().createInvite(islandId, actorUuid, playerUuid).join();
            client.members().inviteSnapshots(playerUuid).join();
            client.navigation().playerIslands(playerUuid).join();
            client.memberCommands().acceptInvite(inviteId, playerUuid).join();
            client.memberCommands().declineInvite(inviteId, playerUuid).join();
            client.memberCommands().banVisitor(islandId, actorUuid, playerUuid, "bad \"visitor\"").join();
            client.memberCommands().pardonVisitor(islandId, actorUuid, playerUuid).join();
            client.memberCommands().kickVisitor(islandId, actorUuid, playerUuid).join();
            client.visitorStats().stats(islandId, 500).join();
            client.settingsCommands().setFlag(islandId, actorUuid, IslandFlag.PVP, "deny \"all\"").join();
            client.environmentCommands().setBiome(islandId, actorUuid, "minecraft:plains\"warm").join();
            client.homeWarpCommands().setHome(islandId, actorUuid, "home\"main", location).join();

            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"Base \\\"One\\\"\"}", requestBodies.get("name"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"role\":\"TRUSTED_MEMBER\",\"roleKey\":\"TRUSTED_MEMBER\"}", requestBodies.get("memberSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"durationSeconds\":30}", requestBodies.get("memberTrust"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"targetUuid\":\"" + playerUuid + "\"}", requestBodies.get("transfer"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("memberRemove"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + actorUuid + "\",\"targetUuid\":\"" + playerUuid + "\"}", requestBodies.get("inviteCreate"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("inviteList"));
            assertEquals("{\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("playerIslands"));
            assertEquals("{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("inviteAccept"));
            assertEquals("{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("inviteDecline"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"reason\":\"bad \\\"visitor\\\"\"}", requestBodies.get("banSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("banRemove"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}", requestBodies.get("visitorKick"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"limit\":100}", requestBodies.get("visitorStats"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"flag\":\"PVP\",\"value\":\"deny \\\"all\\\"\"}", requestBodies.get("flagSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"biomeKey\":\"minecraft:plains\\\"warm\"}", requestBodies.get("biomeSet"));
            assertEquals("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"home\\\"main\",\"worldName\":\"world\\\"home\",\"localX\":10.5,\"localY\":65.0,\"localZ\":-2.25,\"yaw\":180.0,\"pitch\":15.0}", requestBodies.get("homeSet"));
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
