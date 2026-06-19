package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeDrainPolicy;
import kr.lunaf.cloudislands.coreservice.NodeFailureMonitor;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class AdminNodeRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final String PREFIX = "/v1/admin/nodes/";

    private final NodeRegistry nodes;
    private final NodeFailureMonitor nodeFailureMonitor;
    private final Duration heartbeatTimeout;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public AdminNodeRoutes(NodeRegistry nodes, NodeFailureMonitor nodeFailureMonitor, Duration heartbeatTimeout, AuditLogger audit, GlobalEventPublisher events) {
        this.nodes = nodes;
        this.nodeFailureMonitor = nodeFailureMonitor;
        this.heartbeatTimeout = heartbeatTimeout;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/nodes/drain", exchange -> drain(exchange, bodyNodeId(exchange)));
        registry.route("/v1/admin/nodes/undrain", exchange -> undrain(exchange, bodyNodeId(exchange)));
        registry.route("/v1/admin/nodes/kickall", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            kickAll(exchange, JsonFields.text(body, "nodeId", ""), JsonFields.text(body, "reason", "admin-request"));
        });
        registry.route("/v1/admin/nodes/shutdown-safe", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            shutdownSafe(exchange, JsonFields.text(body, "nodeId", ""), JsonFields.text(body, "reason", "admin-request"));
        });
        registry.route("/v1/admin/nodes/sweep", this::sweep);
    }

    public void register(CoreRouteRegistry registry, CoreRouteRegistry prefixRegistry) {
        register(registry);
        prefixRegistry.route(PREFIX, this::prefixRoute);
    }

    private void prefixRoute(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String tail = path.substring(PREFIX.length());
        if (!method.equalsIgnoreCase("POST")) {
            CoreHttpResponses.write(exchange, 405, ApiResponses.error("METHOD_NOT_ALLOWED", "Use POST for admin node lifecycle operations"));
            return;
        }
        if (tail.endsWith("/drain")) {
            drain(exchange, tail.substring(0, tail.length() - "/drain".length()));
            return;
        }
        if (tail.endsWith("/undrain")) {
            undrain(exchange, tail.substring(0, tail.length() - "/undrain".length()));
            return;
        }
        if (tail.endsWith("/kickall")) {
            String body = CoreHttpResponses.readBody(exchange);
            kickAll(exchange, tail.substring(0, tail.length() - "/kickall".length()), JsonFields.text(body, "reason", "admin-request"));
            return;
        }
        if (tail.endsWith("/shutdown-safe")) {
            String body = CoreHttpResponses.readBody(exchange);
            shutdownSafe(exchange, tail.substring(0, tail.length() - "/shutdown-safe".length()), JsonFields.text(body, "reason", "admin-request"));
            return;
        }
        CoreHttpResponses.write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
    }

    private String bodyNodeId(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        return JsonFields.text(CoreHttpResponses.readBody(exchange), "nodeId", "");
    }

    private void drain(com.sun.net.httpserver.HttpExchange exchange, String nodeId) throws IOException {
        boolean changed = nodes.drain(nodeId);
        lifecycle(exchange, nodeId, changed, "DRAINING", "DRAIN", "NODE_DRAIN");
    }

    private void undrain(com.sun.net.httpserver.HttpExchange exchange, String nodeId) throws IOException {
        boolean changed = nodes.undrain(nodeId);
        lifecycle(exchange, nodeId, changed, "READY", "UNDRAIN", "NODE_UNDRAIN");
    }

    private void lifecycle(com.sun.net.httpserver.HttpExchange exchange, String nodeId, boolean changed, String state, String operation, String auditAction) throws IOException {
        if (changed) {
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), nodeLifecycleFields(nodeId, state, operation));
        }
        audit.log(SYSTEM_ACTOR, "ADMIN", auditAction, "NODE", nodeId, nodeLifecycleFields(nodeId, changed ? state : "NOT_FOUND", operation));
        CoreHttpResponses.write(exchange, changed ? 202 : 404, changed ? nodeLifecycleJson(nodeId, state, operation) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
    }

    private void kickAll(com.sun.net.httpserver.HttpExchange exchange, String nodeId, String reason) throws IOException {
        boolean found = nodes.find(nodeId).isPresent();
        if (found) {
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "KICKALL", "reason", reason));
        }
        audit.log(SYSTEM_ACTOR, "ADMIN", "NODE_KICKALL", "NODE", nodeId, Map.of("reason", reason));
        CoreHttpResponses.write(exchange, found ? 202 : 404, found ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
    }

    private void shutdownSafe(com.sun.net.httpserver.HttpExchange exchange, String nodeId, String reason) throws IOException {
        boolean changed = nodes.shutdownSafe(nodeId);
        if (changed) {
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "SHUTTING_DOWN", "operation", "SHUTDOWN_SAFE", "reason", reason));
        }
        audit.log(SYSTEM_ACTOR, "ADMIN", "NODE_SHUTDOWN_SAFE", "NODE", nodeId, Map.of("reason", reason));
        CoreHttpResponses.write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
    }

    private void sweep(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String nodeId = JsonFields.text(body, "nodeId", "");
        int affected = 0;
        int recoveryQueued = 0;
        List<String> downNodes = nodeId.isBlank() ? nodes.markStaleDown(heartbeatTimeout) : List.of(nodeId);
        for (String downNode : downNodes) {
            affected += nodeFailureMonitor.markRecoveryRequiredForNode(downNode);
            recoveryQueued += nodeFailureMonitor.recoverOrQuarantineNodeIslands(downNode);
        }
        String nodesCsv = String.join(",", downNodes);
        audit.log(SYSTEM_ACTOR, "ADMIN", "NODE_SWEEP", "NODE", nodeId.isBlank() ? "*" : nodeId, Map.of("recoveryRequired", Integer.toString(affected), "recoveryQueued", Integer.toString(recoveryQueued), "nodes", nodesCsv));
        events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId.isBlank() ? "*" : nodeId, "state", "SWEEP", "recoveryRequired", Integer.toString(affected), "recoveryQueued", Integer.toString(recoveryQueued), "nodes", nodesCsv));
        CoreHttpResponses.write(exchange, 202, "{\"nodes\":" + nodesJson(downNodes) + ",\"recoveryRequired\":" + affected + ",\"recoveryQueued\":" + recoveryQueued + "}");
    }

    static Map<String, String> nodeLifecycleFields(String nodeId, String state, String operation) {
        return Map.of(
            "nodeId", nodeId == null ? "" : nodeId,
            "state", state == null ? "" : state,
            "operation", operation == null ? "" : operation,
            "drainContract", NodeDrainPolicy.CONTRACT,
            "newRoutePolicy", NodeDrainPolicy.NEW_ROUTE_POLICY,
            "activeIslandPolicy", NodeDrainPolicy.ACTIVE_ISLAND_POLICY,
            "nextStep", operation != null && operation.equals("UNDRAIN") ? NodeDrainPolicy.UNDRAIN_NEXT_STEP : NodeDrainPolicy.DRAIN_NEXT_STEP
        );
    }

    static String nodeLifecycleJson(String nodeId, String state, String operation) {
        String nextStep = operation != null && operation.equals("UNDRAIN")
            ? NodeDrainPolicy.UNDRAIN_NEXT_STEP
            : NodeDrainPolicy.DRAIN_NEXT_STEP;
        return "{\"accepted\":true"
            + ",\"nodeId\":\"" + escape(nodeId == null ? "" : nodeId) + "\""
            + ",\"state\":\"" + escape(state == null ? "" : state) + "\""
            + ",\"operation\":\"" + escape(operation == null ? "" : operation) + "\""
            + ",\"drainContract\":\"" + escape(NodeDrainPolicy.CONTRACT) + "\""
            + ",\"newRoutePolicy\":\"" + escape(NodeDrainPolicy.NEW_ROUTE_POLICY) + "\""
            + ",\"activeIslandPolicy\":\"" + escape(NodeDrainPolicy.ACTIVE_ISLAND_POLICY) + "\""
            + ",\"ownerMemberPolicy\":\"" + escape(NodeDrainPolicy.OWNER_MEMBER_POLICY) + "\""
            + ",\"visitorPolicy\":\"" + escape(NodeDrainPolicy.VISITOR_POLICY) + "\""
            + ",\"nextStep\":\"" + escape(nextStep) + "\""
            + "}";
    }

    static String nodesJson(List<String> nodes) {
        return "[\"" + String.join("\",\"", nodes.stream().map(value -> value.replace("\"", "'")).toList()) + "\"]";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
