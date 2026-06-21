package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.ProtocolVersion;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

class ProtocolRoutesTest {
    @Test
    void registersProtocolEndpointGroup() {
        List<String> paths = new ArrayList<>();
        ProtocolRoutes routes = new ProtocolRoutes(null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/nodes/heartbeat"));
        assertTrue(paths.contains("/v1/admin/protocol"));
    }

    @Test
    void rendersProtocolStatusAndNegotiation() {
        Map<?, ?> status = SimpleJson.object(SimpleJson.parse(ProtocolRoutes.protocolStatusJson()));
        Map<?, ?> rejected = SimpleJson.object(SimpleJson.parse(
            ProtocolRoutes.protocolNegotiationJson(ProtocolVersion.negotiate(NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION + 1))
        ));

        assertEquals(true, status.get("success"));
        assertEquals("/v1/nodes/heartbeat", SimpleJson.text(status.get("nodeHeartbeatEndpoint")));
        assertEquals("PROTOCOL_VERSION_UNSUPPORTED", SimpleJson.text(rejected.get("code")));
        assertEquals(false, rejected.get("accepted"));
    }

    @Test
    void parsesHeartbeatDefaultsAndFields() {
        NodeHeartbeatRequest heartbeat = ProtocolRoutes.parseHeartbeat("{\"nodeId\":\"island-2\",\"state\":\"DRAINING\",\"players\":12,\"storageAvailable\":false}");

        assertEquals(NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION, heartbeat.protocolVersion());
        assertEquals("island-2", heartbeat.nodeId());
        assertEquals("island-2", heartbeat.velocityServerName());
        assertEquals(NodeState.DRAINING, heartbeat.state());
        assertEquals(12, heartbeat.players());
        assertEquals(false, heartbeat.storageAvailable());
    }
}
