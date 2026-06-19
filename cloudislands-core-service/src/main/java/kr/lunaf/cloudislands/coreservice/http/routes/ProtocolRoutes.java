package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.protocol.ProtocolVersion;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class ProtocolRoutes implements RouteGroup {
    private final NodeRegistry nodes;

    public ProtocolRoutes(NodeRegistry nodes) {
        this.nodes = nodes;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/nodes/heartbeat", this::heartbeat);
        registry.route("/v1/admin/protocol", exchange -> CoreHttpResponses.write(exchange, 200, protocolStatusJson()));
    }

    private void heartbeat(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        NodeHeartbeatRequest heartbeat = parseHeartbeat(CoreHttpResponses.readBody(exchange));
        ProtocolVersion.NegotiationResult negotiation = ProtocolVersion.negotiate(heartbeat.protocolVersion());
        if (!negotiation.accepted()) {
            CoreHttpResponses.write(exchange, 426, protocolNegotiationJson(negotiation));
            return;
        }
        nodes.heartbeat(heartbeat);
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    static String protocolNegotiationJson(ProtocolVersion.NegotiationResult negotiation) {
        return "{"
            + "\"success\":false,"
            + "\"code\":\"PROTOCOL_VERSION_UNSUPPORTED\","
            + "\"message\":\"Node protocol version is not supported\","
            + "\"clientVersion\":" + negotiation.clientVersion() + ","
            + "\"minSupported\":" + negotiation.minSupported() + ","
            + "\"current\":" + negotiation.current() + ","
            + "\"accepted\":" + negotiation.accepted() + ","
            + "\"status\":\"" + escape(negotiation.status()) + "\","
            + "\"field\":\"" + escape(negotiation.field()) + "\","
            + "\"policy\":\"" + escape(negotiation.policy()) + "\","
            + "\"upgradeHint\":\"" + escape(negotiation.upgradeHint()) + "\""
            + "}";
    }

    static String protocolStatusJson() {
        ProtocolVersion.NegotiationResult current = ProtocolVersion.negotiate(NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION);
        return "{"
            + "\"success\":true,"
            + "\"nodeProtocolMinSupported\":" + current.minSupported() + ","
            + "\"nodeProtocolCurrent\":" + current.current() + ","
            + "\"nodeProtocolHeartbeatField\":\"" + escape(current.field()) + "\","
            + "\"nodeProtocolNegotiationPolicy\":\"" + escape(current.policy()) + "\","
            + "\"nodeProtocolCurrentAccepted\":" + current.accepted() + ","
            + "\"nodeProtocolCurrentStatus\":\"" + escape(current.status()) + "\","
            + "\"nodeProtocolUpgradeHint\":\"" + escape(current.upgradeHint()) + "\","
            + "\"nodeHeartbeatEndpoint\":\"/v1/nodes/heartbeat\""
            + "}";
    }

    static NodeHeartbeatRequest parseHeartbeat(String body) {
        return new NodeHeartbeatRequest(
            JsonFields.integer(body, "protocolVersion", NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION),
            JsonFields.text(body, "nodeId", "unknown"),
            JsonFields.text(body, "pool", "island"),
            JsonFields.text(body, "velocityServerName", JsonFields.text(body, "nodeId", "unknown")),
            JsonFields.text(body, "nodeVersion", ""),
            JsonFields.enumValue(NodeState.class, body, "state", NodeState.READY),
            JsonFields.integer(body, "players", 0),
            JsonFields.integer(body, "softPlayerCap", 90),
            JsonFields.integer(body, "hardPlayerCap", 110),
            JsonFields.integer(body, "reservedSlots", 0),
            JsonFields.integer(body, "activeIslands", 0),
            JsonFields.integer(body, "maxActiveIslands", 600),
            JsonFields.decimal(body, "mspt", 20.0D),
            JsonFields.integer(body, "activationQueue", 0),
            JsonFields.integer(body, "maxActivationQueue", 20),
            JsonFields.decimal(body, "chunkLoadPressure", 0.0D),
            JsonFields.longValue(body, "heapUsedMb", 0L),
            JsonFields.longValue(body, "heapMaxMb", 1L),
            JsonFields.integer(body, "recentFailurePenalty", 0),
            JsonFields.bool(body, "storageAvailable", true),
            JsonFields.text(body, "supportedTemplates", "*")
        );
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
