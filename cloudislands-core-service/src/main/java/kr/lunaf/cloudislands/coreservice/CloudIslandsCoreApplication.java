package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class CloudIslandsCoreApplication {
    private final HttpServer server;
    private final ApiTokenGuard tokenGuard;
    private final FixedWindowRateLimiter rateLimiter;
    private final kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard adminGuard;
    private final kr.lunaf.cloudislands.coreservice.security.IpAllowlist ipAllowlist;

    public CloudIslandsCoreApplication(int port) throws IOException {
        Clock clock = Clock.systemUTC();
        this.tokenGuard = new ApiTokenGuard(System.getenv().getOrDefault("CI_CORE_TOKEN", ""));
        this.rateLimiter = new FixedWindowRateLimiter(clock, 240, 60_000L);
        this.adminGuard = new kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard(System.getenv().getOrDefault("CI_ADMIN_TOKEN", ""));
        this.ipAllowlist = new kr.lunaf.cloudislands.coreservice.security.IpAllowlist(System.getenv().getOrDefault("CI_IP_ALLOWLIST", ""));
        InMemoryNodeRegistry nodes = new InMemoryNodeRegistry();
        NodeAllocator allocator = new NodeAllocator(Duration.ofSeconds(5));
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(clock);
        InMemoryRouteSessionStore sessions = new InMemoryRouteSessionStore(clock);
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, allocator, tickets);
        CreateIslandWorkflow createIsland = new CreateIslandWorkflow(new InMemoryIslandRepository(), nodes, allocator, jobs, events);
        IslandLifecycleWorkflow lifecycle = new IslandLifecycleWorkflow(new InMemoryIslandRuntimeRepository(), nodes, allocator, jobs, events);
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        route("/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        route("/v1/nodes", exchange -> write(exchange, 200, nodes.toJson()));
        route("/v1/jobs", exchange -> write(exchange, 200, jobs.toJson()));
        route("/v1/jobs/claim", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            java.util.List<kr.lunaf.cloudislands.protocol.job.IslandJob> claimed = jobs.claim(nodeId, java.util.List.of(kr.lunaf.cloudislands.protocol.job.IslandJobType.CREATE_ISLAND, kr.lunaf.cloudislands.protocol.job.IslandJobType.ACTIVATE_ISLAND, kr.lunaf.cloudislands.protocol.job.IslandJobType.DEACTIVATE_ISLAND, kr.lunaf.cloudislands.protocol.job.IslandJobType.SNAPSHOT_ISLAND, kr.lunaf.cloudislands.protocol.job.IslandJobType.MIGRATE_ISLAND), JsonFields.integer(body, "maxJobs", 4));
            write(exchange, 200, kr.lunaf.cloudislands.protocol.job.json.IslandJobJson.writeArray(claimed));
        });
        route("/v1/jobs/complete", exchange -> {
            String body = readBody(exchange);
            jobs.complete(JsonFields.text(body, "nodeId", ""), JsonFields.uuid(body, "jobId", new UUID(0L, 0L)));
            write(exchange, 202, "{\"accepted\":true}");
        });
        route("/v1/jobs/fail", exchange -> {
            String body = readBody(exchange);
            jobs.fail(JsonFields.text(body, "nodeId", ""), JsonFields.uuid(body, "jobId", new UUID(0L, 0L)), JsonFields.text(body, "error", "unknown"));
            write(exchange, 202, "{\"accepted\":true}");
        });
        route("/v1/events", exchange -> write(exchange, 200, events.toJson()));
        route("/v1/audit", exchange -> write(exchange, 200, audit.toJson()));
        route("/v1/routes/home", exchange -> {
            String body = readBody(exchange);
            write(exchange, 202, routing.prepareHomeRouteJson(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L))));
        });
        route("/v1/routes/visit", exchange -> {
            String body = readBody(exchange);
            write(exchange, 202, routing.prepareVisitRouteJson(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L))));
        });
        route("/v1/routes/session", exchange -> {
            String body = readBody(exchange);
            sessions.put(new RouteTicket(
                JsonFields.uuid(body, "ticketId", UUID.randomUUID()),
                JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)),
                kr.lunaf.cloudislands.api.model.RouteAction.HOME,
                new UUID(0L, 0L),
                JsonFields.text(body, "targetNode", ""),
                "ci_shard_001",
                kr.lunaf.cloudislands.api.model.RouteTicketState.READY,
                java.time.Instant.parse(JsonFields.text(body, "expiresAt", java.time.Instant.now().plusSeconds(30).toString())),
                JsonFields.text(body, "nonce", ""),
                Map.of("targetServerName", JsonFields.text(body, "targetServerName", ""))
            ));
            write(exchange, 202, "{\"accepted\":true}");
        });
        route("/v1/routes/session/consume", exchange -> {
            String body = readBody(exchange);
            PlayerRouteSession session = sessions.consume(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.text(body, "nodeId", "")).orElse(null);
            write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
        });
        route("/v1/routes/consume", exchange -> write(exchange, 200, routing.consumeTicketJson(readBody(exchange))));
        route("/v1/nodes/heartbeat", exchange -> {
            nodes.heartbeat(heartbeat(readBody(exchange)));
            write(exchange, 202, "{\"accepted\":true}");
        });
        route("/v1/admin/nodes/drain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.drain(nodeId);
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_DRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, "{\"accepted\":" + changed + "}");
        });
        route("/v1/admin/nodes/undrain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.undrain(nodeId);
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_UNDRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, "{\"accepted\":" + changed + "}");
        });
        route("/v1/admin/islands/activate", exchange -> lifecycle(exchange, lifecycle.activate(JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L)))));
        route("/v1/admin/islands/deactivate", exchange -> lifecycle(exchange, lifecycle.deactivate(JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L)))));
        route("/v1/admin/islands/migrate", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.migrate(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "targetNode", "")));
        });
        route("/v1/admin/islands/snapshot", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.snapshot(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "reason", "MANUAL")));
        });
        route("/v1/admin/islands/quarantine", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.quarantine(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "reason", "admin")));
        });
        route("/v1/islands", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            CreateIslandResult result = createIsland.create(playerUuid, JsonFields.text(body, "templateId", "default"));
            audit.log(playerUuid, "PLAYER", "ISLAND_CREATE", "ISLAND", result.island() == null ? "" : result.island().islandId().toString(), Map.of("code", result.code()));
            String ticketJson = result.ticket() == null ? "null" : RoutingOrchestrator.toJson(result.ticket());
            String islandId = result.island() == null ? "" : result.island().islandId().toString();
            write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"islandId\":\"" + islandId + "\",\"ticket\":" + ticketJson + "}");
        });
    }

    public void start() {
        server.start();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8443 : Integer.parseInt(args[0]);
        new CloudIslandsCoreApplication(port).start();
    }

    private void route(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            String key = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(key)) {
                write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!path.equals("/health") && !tokenGuard.allowed(exchange)) {
                write(exchange, 401, ApiResponses.error("UNAUTHORIZED", "Missing or invalid API token"));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(path, exchange)) {
                write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            handler.handle(exchange);
        });
    }

    private static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\"}");
    }

    private static String sessionJson(PlayerRouteSession session) {
        return "{\"playerUuid\":\"" + session.playerUuid() + "\",\"ticketId\":\"" + session.ticketId() + "\",\"targetNode\":\"" + session.targetNode() + "\",\"targetServerName\":\"" + session.targetServerName() + "\",\"nonce\":\"" + session.nonce() + "\",\"expiresAt\":\"" + session.expiresAt() + "\"}";
    }

    private static NodeHeartbeatRequest heartbeat(String body) {
        return new NodeHeartbeatRequest(
            JsonFields.text(body, "nodeId", "unknown"),
            JsonFields.text(body, "pool", "island"),
            JsonFields.text(body, "velocityServerName", JsonFields.text(body, "nodeId", "unknown")),
            JsonFields.enumValue(NodeState.class, body, "state", NodeState.READY),
            JsonFields.integer(body, "players", 0),
            JsonFields.integer(body, "activeIslands", 0),
            JsonFields.decimal(body, "mspt", 20.0D),
            JsonFields.integer(body, "activationQueue", 0),
            JsonFields.longValue(body, "heapUsedMb", 0L),
            JsonFields.longValue(body, "heapMaxMb", 1L)
        );
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of("application/json; charset=utf-8"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
