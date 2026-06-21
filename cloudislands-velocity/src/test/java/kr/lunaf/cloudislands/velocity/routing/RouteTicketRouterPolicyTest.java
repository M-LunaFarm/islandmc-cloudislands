package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import net.kyori.adventure.text.Component;
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

    @Test
    void readyTicketClearsCoreRouteWhenTargetServerIsMissing() {
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        List<String> coreCalls = new ArrayList<>();
        Player player = player(playerUuid);
        CoreApiClient core = coreClient(coreCalls);
        RouteTicketRouter router = new RouteTicketRouter(
            core,
            1,
            VelocityMessages.defaults(),
            new VelocityRoutingMetrics(),
            new RouteFallbackService(proxy(player), "lobby", new VelocityRoutingMetrics(), Component::text),
            new RouteProgressPresenter(false, false, Component::text)
        );
        RouteTicket ticket = new RouteTicket(
            ticketId,
            playerUuid,
            RouteAction.HOME,
            UUID.randomUUID(),
            "island-node-1",
            "ci_shard_001",
            RouteTicketState.READY,
            Instant.now().plusSeconds(30),
            "nonce-1",
            Map.of("targetServerName", "missing-island-server")
        );

        router.route(player, ticket, "fallback");

        assertTrue(coreCalls.contains("publishRouteSession:" + ticketId));
        assertTrue(coreCalls.contains("clearRoute:" + ticketId + ":TARGET_SERVER_NOT_FOUND"));
    }

    private static CoreApiClient coreClient(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> {
                if (method.getName().equals("publishRouteSession")) {
                    RouteTicket ticket = (RouteTicket) args[0];
                    calls.add("publishRouteSession:" + ticket.ticketId());
                    return CompletableFuture.completedFuture(null);
                }
                if (method.getName().equals("clearRoute") && args.length == 3) {
                    calls.add("clearRoute:" + args[1] + ":" + args[2]);
                    return CompletableFuture.completedFuture("{\"cleared\":true}");
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static ProxyServer proxy(Player player) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getPlayer")) {
                UUID playerUuid = (UUID) args[0];
                return player.getUniqueId().equals(playerUuid) ? Optional.of(player) : Optional.empty();
            }
            if (method.getName().equals("getServer")) {
                return Optional.empty();
            }
            return defaultValue(method.getReturnType());
        };
        return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[] {ProxyServer.class}, handler);
    }

    private static Player player(UUID playerUuid) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getUniqueId")) {
                return playerUuid;
            }
            if (method.getName().equals("sendMessage") || method.getName().equals("sendActionBar")) {
                return null;
            }
            if (method.getName().equals("toString")) {
                return "TestPlayer[" + playerUuid + "]";
            }
            return defaultValue(method.getReturnType());
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
