package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.NodeState;
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
        String status = ProtocolRoutes.protocolStatusJson();
        String rejected = ProtocolRoutes.protocolNegotiationJson(ProtocolVersion.negotiate(NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION + 1));

        assertTrue(status.contains("\"success\":true"));
        assertTrue(status.contains("\"nodeHeartbeatEndpoint\":\"/v1/nodes/heartbeat\""));
        assertTrue(rejected.contains("\"code\":\"PROTOCOL_VERSION_UNSUPPORTED\""));
        assertTrue(rejected.contains("\"accepted\":false"));
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
