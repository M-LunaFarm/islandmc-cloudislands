package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
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
        List<Object> renderedRuntimes = new ArrayList<>();
        for (IslandRuntimeSnapshot runtime : runtimes) {
            renderedRuntimes.add(runtimeMap(runtime));
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("nodeId", nodeId);
        values.put("count", runtimes.size());
        values.put("islands", renderedRuntimes);
        return SimpleJson.stringify(values);
    }

    private static Map<String, Object> runtimeMap(IslandRuntimeSnapshot runtime) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", runtime.islandId());
        values.put("state", runtime.state());
        values.put("activeNode", runtime.activeNode());
        values.put("activeWorld", runtime.activeWorld());
        values.put("cellX", runtime.cellX());
        values.put("cellZ", runtime.cellZ());
        values.put("leaseOwner", runtime.leaseOwner());
        values.put("fencingToken", runtime.fencingToken());
        values.put("activatedAt", runtime.activatedAt());
        values.put("lastHeartbeat", runtime.lastHeartbeat());
        return values;
    }
}
