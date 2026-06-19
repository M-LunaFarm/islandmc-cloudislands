package kr.lunaf.cloudislands.velocity;

import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityRoutingControllerTest {
    @Test
    void hidesRouteDiagnosticNodeFieldsWhenConfigured() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                true,
                "island",
                30
        );

        assertEquals("", privateString(controller, "routeNodeSuffix", "island-2"));
        assertEquals("", privateString(controller, "routeRequestedNodeSuffix", "island-3"));
        assertEquals("", privateString(controller, "routeServerSuffix", "island-4"));
        assertEquals("", privateString(controller, "runtimeWorldSuffix", "ci_island_shard_03"));
        assertEquals("", privateString(controller, "runtimeCellSuffix", "{\"cellX\":12,\"cellZ\":7}"));
    }

    @Test
    void sanitizesPhysicalNodeNamesFromPlayerMessages() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                true,
                "island",
                30
        );

        String message = privateString(controller, "playerMessage", "Island-1 준비 중, island_2 로 이동합니다.");

        assertFalse(message.contains("Island-1"));
        assertFalse(message.contains("island_2"));
    }

    @Test
    void sanitizesRouteSummaryReasonsWhenNodeNamesAreHidden() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                true,
                "island",
                30
        );

        String message = privateString(controller, "routeClearMessage", "{\"clearedSession\":true,\"clearedTicket\":true,\"reason\":\"targetNode=Island-2 backendServer=island-5\"}");

        assertFalse(message.contains("Island-2"));
        assertFalse(message.contains("island-5"));
        assertTrue(message.contains("targetNode=hidden"));
        assertTrue(message.contains("backendServer=hidden"));
    }

    @Test
    void routeTargetNamesUsePlayerRouteViews() throws Exception {
        VelocityRoutingController controller = new VelocityRoutingController(
                null,
                null,
                "Lobby",
                20,
                true,
                true,
                true,
                "island",
                30
        );
        RouteTicket ticket = new RouteTicket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RouteAction.VISIT,
                UUID.randomUUID(),
                "island-2",
                "ci_island_shard_03",
                RouteTicketState.READY,
                Instant.now().plusSeconds(30),
                "nonce-secret",
                Map.of(
                        "surface", "island-warps",
                        "targetServerName", "Island-2",
                        "route-ticket", "secret"
                )
        );

        assertEquals("섬 워프", routeTargetName(controller, ticket));
    }

    private String privateString(VelocityRoutingController controller, String methodName, String value) throws Exception {
        Method method = VelocityRoutingController.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, value);
    }

    private String routeTargetName(VelocityRoutingController controller, RouteTicket ticket) throws Exception {
        Method method = VelocityRoutingController.class.getDeclaredMethod("routeTargetName", RouteTicket.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, ticket);
    }
}
