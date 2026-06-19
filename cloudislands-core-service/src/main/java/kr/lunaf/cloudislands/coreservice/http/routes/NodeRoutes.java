package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;

public final class NodeRoutes implements RouteGroup {
    private final NodeRegistry nodes;
    private final Duration heartbeatTimeout;
    private final IslandRuntimeRepository runtimes;

    public NodeRoutes(NodeRegistry nodes, Duration heartbeatTimeout, IslandRuntimeRepository runtimes) {
        this.nodes = nodes;
        this.heartbeatTimeout = heartbeatTimeout;
        this.runtimes = runtimes;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/storage", exchange -> CoreHttpResponses.write(exchange, 200, nodes.toJson(heartbeatTimeout)));
        registry.route("/v1/nodes", exchange -> CoreHttpResponses.write(exchange, 200, nodes.toJson(heartbeatTimeout)));
        registry.route("/v1/nodes/info", this::nodeInfo);
        registry.route("/v1/nodes/islands", this::nodeIslands);
        registry.route("/v1/admin/nodes/list", exchange -> CoreHttpResponses.write(exchange, 200, nodes.toJson(heartbeatTimeout)));
        registry.route("/v1/admin/nodes/info", this::nodeInfo);
        registry.route("/v1/admin/nodes/islands", this::nodeIslands);
    }

    private void nodeInfo(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String nodeId = JsonFields.text(body, "nodeId", "");
        java.util.Optional<NodeLoad> node = nodes.find(nodeId);
        CoreHttpResponses.write(
            exchange,
            node.isPresent() ? 200 : 404,
            node.map(NodeRegistry::toJson).orElseGet(() -> ApiResponses.error("NODE_NOT_FOUND", "Node was not found"))
        );
    }

    private void nodeIslands(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String nodeId = JsonFields.text(body, "nodeId", "");
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 50), 200));
        if (nodes.find(nodeId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
            return;
        }
        CoreHttpResponses.write(exchange, 200, nodeIslandsJson(nodeId, runtimes.listByNode(nodeId, limit)));
    }

    static String nodeIslandsJson(String nodeId, List<IslandRuntimeSnapshot> runtimes) {
        StringBuilder builder = new StringBuilder("{\"nodeId\":\"").append(escape(nodeId)).append("\",\"count\":").append(runtimes.size()).append(",\"islands\":[");
        boolean first = true;
        for (IslandRuntimeSnapshot runtime : runtimes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(runtimeJson(runtime));
        }
        return builder.append("]}").toString();
    }

    private static String runtimeJson(IslandRuntimeSnapshot runtime) {
        return "{\"islandId\":\"" + runtime.islandId()
            + "\",\"state\":\"" + runtime.state()
            + "\",\"activeNode\":" + nullable(runtime.activeNode())
            + ",\"activeWorld\":" + nullable(runtime.activeWorld())
            + ",\"cellX\":" + (runtime.cellX() == null ? "null" : runtime.cellX())
            + ",\"cellZ\":" + (runtime.cellZ() == null ? "null" : runtime.cellZ())
            + ",\"leaseOwner\":" + nullable(runtime.leaseOwner())
            + ",\"fencingToken\":" + runtime.fencingToken()
            + ",\"activatedAt\":" + nullable(runtime.activatedAt() == null ? null : runtime.activatedAt().toString())
            + ",\"lastHeartbeat\":" + nullable(runtime.lastHeartbeat() == null ? null : runtime.lastHeartbeat().toString())
            + "}";
    }

    private static String nullable(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
