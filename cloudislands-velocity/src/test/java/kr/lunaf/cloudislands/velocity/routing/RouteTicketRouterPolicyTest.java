package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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

        int publish = source.indexOf("routingCommands.publishRouteSession(ticket)");
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
        assertTrue(source.contains("routingCommands.clearRoute(ticket, reason"), "failure cleanup must call Core route clear with an explicit reason");
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

    @Test
    void readyTicketPublishesRouteSessionBeforeConnectingToTargetServer() {
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        Player player = player(playerUuid, calls);
        RegisteredServer targetServer = registeredServer("island-velocity-1");
        CoreApiClient core = coreClient(calls);
        RouteTicketRouter router = new RouteTicketRouter(
            core,
            1,
            VelocityMessages.defaults(),
            new VelocityRoutingMetrics(),
            new RouteFallbackService(proxy(player, "island-velocity-1", targetServer), "lobby", new VelocityRoutingMetrics(), Component::text),
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
            Map.of("targetServerName", "island-velocity-1")
        );

        router.route(player, ticket, "fallback");

        assertTrue(calls.indexOf("publishRouteSession:" + ticketId) >= 0);
        assertTrue(calls.indexOf("connectRequest:island-velocity-1") > calls.indexOf("publishRouteSession:" + ticketId));
        assertTrue(calls.indexOf("connectWithIndication:island-velocity-1") > calls.indexOf("connectRequest:island-velocity-1"));
    }

    private static CoreApiClient coreClient(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> {
                if (method.getName().equals("publishRouteSessionResult")) {
                    RouteTicket ticket = (RouteTicket) args[0];
                    calls.add("publishRouteSession:" + ticket.ticketId());
                    return CompletableFuture.completedFuture("{\"ok\":true}");
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
        return proxy(player, "", null);
    }

    private static ProxyServer proxy(Player player, String serverName, RegisteredServer server) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getPlayer")) {
                UUID playerUuid = (UUID) args[0];
                return player.getUniqueId().equals(playerUuid) ? Optional.of(player) : Optional.empty();
            }
            if (method.getName().equals("getServer")) {
                return server != null && serverName.equals(args[0]) ? Optional.of(server) : Optional.empty();
            }
            return defaultValue(method.getReturnType());
        };
        return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[] {ProxyServer.class}, handler);
    }

    private static Player player(UUID playerUuid) {
        return player(playerUuid, new ArrayList<>());
    }

    private static Player player(UUID playerUuid, List<String> calls) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getUniqueId")) {
                return playerUuid;
            }
            if (method.getName().equals("createConnectionRequest")) {
                String serverName = serverName((RegisteredServer) args[0]);
                calls.add("connectRequest:" + serverName);
                return connectionRequest((RegisteredServer) args[0], serverName, calls);
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

    private static RegisteredServer registeredServer(String serverName) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getServerInfo")) {
                return null;
            }
            if (method.getName().equals("toString")) {
                return serverName;
            }
            return defaultValue(method.getReturnType());
        };
        return (RegisteredServer) Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(), new Class<?>[] {RegisteredServer.class}, handler);
    }

    private static ConnectionRequestBuilder connectionRequest(RegisteredServer server, String serverName, List<String> calls) {
        InvocationHandler handler = (_proxy, method, args) -> {
            if (method.getName().equals("getServer")) {
                return server;
            }
            if (method.getName().equals("connectWithIndication")) {
                calls.add("connectWithIndication:" + serverName);
                return CompletableFuture.completedFuture(true);
            }
            if (method.getName().equals("connect")) {
                calls.add("connect:" + serverName);
                return CompletableFuture.completedFuture(null);
            }
            if (method.getName().equals("fireAndForget")) {
                calls.add("fireAndForget:" + serverName);
                return null;
            }
            return defaultValue(method.getReturnType());
        };
        return (ConnectionRequestBuilder) Proxy.newProxyInstance(ConnectionRequestBuilder.class.getClassLoader(), new Class<?>[] {ConnectionRequestBuilder.class}, handler);
    }

    private static String serverName(RegisteredServer server) {
        return server == null ? "" : server.toString();
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
