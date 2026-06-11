package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class CloudIslandsCoreApplication {
    private final HttpServer server;

    public CloudIslandsCoreApplication(int port) throws IOException {
        InMemoryNodeRegistry nodes = new InMemoryNodeRegistry();
        NodeAllocator allocator = new NodeAllocator(Duration.ofSeconds(5));
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(java.time.Clock.systemUTC());
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, allocator, tickets);
        CreateIslandWorkflow createIsland = new CreateIslandWorkflow(new InMemoryIslandRepository(), nodes, allocator, jobs, events);
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/v1/nodes", exchange -> write(exchange, 200, nodes.toJson()));
        server.createContext("/v1/jobs", exchange -> write(exchange, 200, jobs.toJson()));
        server.createContext("/v1/events", exchange -> write(exchange, 200, events.toJson()));
        server.createContext("/v1/routes/home", exchange -> {
            String body = readBody(exchange);
            write(exchange, 202, routing.prepareHomeRouteJson(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L))));
        });
        server.createContext("/v1/routes/visit", exchange -> {
            String body = readBody(exchange);
            write(exchange, 202, routing.prepareVisitRouteJson(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L))));
        });
        server.createContext("/v1/routes/consume", exchange -> write(exchange, 200, routing.consumeTicketJson(readBody(exchange))));
        server.createContext("/v1/nodes/heartbeat", exchange -> {
            nodes.heartbeat(heartbeat(readBody(exchange)));
            write(exchange, 202, "{\"accepted\":true}");
        });
        server.createContext("/v1/admin/nodes/drain", exchange -> {
            boolean changed = nodes.drain(JsonFields.text(readBody(exchange), "nodeId", ""));
            write(exchange, changed ? 202 : 404, "{\"accepted\":" + changed + "}");
        });
        server.createContext("/v1/admin/nodes/undrain", exchange -> {
            boolean changed = nodes.undrain(JsonFields.text(readBody(exchange), "nodeId", ""));
            write(exchange, changed ? 202 : 404, "{\"accepted\":" + changed + "}");
        });
        server.createContext("/v1/islands", exchange -> {
            String body = readBody(exchange);
            CreateIslandResult result = createIsland.create(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.text(body, "templateId", "default"));
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
