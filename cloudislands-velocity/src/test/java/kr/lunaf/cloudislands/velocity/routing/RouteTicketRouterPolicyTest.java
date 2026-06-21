package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RouteTicketRouterPolicyTest {
    @Test
    void readyTicketsPublishSessionBeforeVelocityConnect() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouter.java"));

        int publish = source.indexOf("coreApiClient.publishRouteSession(ticket)");
        int connect = source.indexOf("connectWithTicket(player, ticket, targetServerName)");

        assertTrue(publish > 0, "READY route tickets must publish a Core route session");
        assertTrue(connect > publish, "Velocity must only connect after Core accepted the route session");
        assertTrue(source.contains("ticket.payload().getOrDefault(\"targetServerName\", ticket.targetNode())"), "Velocity must route to the published server name when Core provides one");
    }

    @Test
    void routeFailuresClearCoreRouteStateBeforeFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouter.java"));

        for (String reason : java.util.List.of(
            "PLAYER_DISCONNECTED",
            "SESSION_PUBLISH_FAILED",
            "TARGET_SERVER_NOT_FOUND",
            "CONNECT_FAILED",
            "CONNECT_EXCEPTION",
            "ROUTE_READY_TIMEOUT",
            "ROUTE_STATUS_FAILED"
        )) {
            assertTrue(source.contains("clearFailedRoute(ticket, \"" + reason + "\")"), reason);
        }
        assertTrue(source.contains("coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason"), "failure cleanup must call Core route clear with an explicit reason");
    }
}
