package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
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
import kr.lunaf.cloudislands.coreclient.RouteClearView;
import kr.lunaf.cloudislands.coreclient.RoutePublishView;
import kr.lunaf.cloudislands.coreclient.RoutingCommandClient;
import org.junit.jupiter.api.Test;

class IslandRoutingUseCaseTest {
    @Test
    void routeOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        RouteTicket ticket = ticket();
        IslandRoutingUseCase useCase = new IslandRoutingUseCase(client(calls, ticket));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000050");
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals(ticket, useCase.createWarpTicket(playerUuid, islandId, "spawn", mutationRunner(calls)).join());
        assertEquals(Optional.of(ticket), useCase.routeTicketStatus(ticket).join());
        assertEquals(null, useCase.publishRouteSession(ticket, mutationRunner(calls)).join());
        assertEquals("cleared", useCase.clearRouteAction(ticket, "", mutationRunner(calls)).join().code());

        assertEquals(List.of(
            "audit:route.ticket.warp",
            "createWarpTicket:spawn",
            "routeTicketStatus:nonce",
            "audit:route.session.publish",
            "publishRouteSession:" + ticket.ticketId(),
            "audit:route.clear",
            "clearRoute:PLUGIN_MESSAGE_FAILED"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls, RouteTicket ticket) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "routingCommands" -> routingCommands(calls, ticket);
                case "createWarpTicket" -> {
                    calls.add("createWarpTicket:" + args[2]);
                    yield CompletableFuture.completedFuture(ticket);
                }
                case "routeTicketStatus" -> {
                    calls.add("routeTicketStatus:" + args[2]);
                    yield CompletableFuture.completedFuture(Optional.of(ticket));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static RoutingCommandClient routingCommands(List<String> calls, RouteTicket ticket) {
        return new RoutingCommandClient() {
            @Override
            public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
                calls.add("createWarpTicket:" + warpName);
                return CompletableFuture.completedFuture(ticket);
            }

            @Override
            public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket routeTicket) {
                calls.add("routeTicketStatus:" + routeTicket.nonce());
                return CompletableFuture.completedFuture(Optional.of(ticket));
            }

            @Override
            public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
                return CompletableFuture.completedFuture(Optional.of(ticket));
            }

            @Override
            public CompletableFuture<Void> publishRouteSession(RouteTicket routeTicket) {
                calls.add("publishRouteSession:" + routeTicket.ticketId());
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<RoutePublishView> publishRouteSessionResult(RouteTicket routeTicket) {
                calls.add("publishRouteSession:" + routeTicket.ticketId());
                return CompletableFuture.completedFuture(new RoutePublishView(true, "ROUTE_SESSION_PUBLISHED"));
            }

            @Override
            public CompletableFuture<RouteClearView> clearRoute(RouteTicket routeTicket, String reason) {
                calls.add("clearRoute:" + (reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason));
                return CompletableFuture.completedFuture(new RouteClearView(true, "cleared"));
            }

            @Override
            public CompletableFuture<RouteClearView> clearRoute(UUID playerUuid, UUID ticketId, String reason) {
                calls.add("clearRoute:" + (reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason));
                return CompletableFuture.completedFuture(new RouteClearView(true, "cleared"));
            }
        };
    }

    private static IslandRoutingUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandRoutingUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static RouteTicket ticket() {
        return new RouteTicket(
            uuid("00000000-0000-0000-0000-000000000051"),
            uuid("00000000-0000-0000-0000-000000000001"),
            RouteAction.WARP,
            uuid("00000000-0000-0000-0000-000000000050"),
            "island-1",
            "world",
            RouteTicketState.READY,
            Instant.now().plusSeconds(30),
            "nonce",
            Map.of("targetServerName", "island-1")
        );
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
