package kr.lunaf.cloudislands.paper.application;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RouteClearView;
import kr.lunaf.cloudislands.coreclient.RoutingCommandClient;

public final class IslandRoutingUseCase {
    private final CoreApiClient coreApiClient;
    private final RoutingCommandClient routingCommands;

    public IslandRoutingUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.routingCommands = coreApiClient.routingCommands();
    }

    IslandRoutingUseCase(CoreApiClient coreApiClient, RoutingCommandClient routingCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (routingCommands == null) {
            throw new IllegalArgumentException("routingCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.routingCommands = routingCommands;
    }

    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName, MutationRunner runner) {
        requireUuid(playerUuid, "playerUuid");
        requireUuid(islandId, "islandId");
        requireRunner(runner);
        return runner.mutate("route.ticket.warp", () -> routingCommands.createWarpTicket(playerUuid, islandId, warpName == null ? "" : warpName));
    }

    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket) {
        requireTicket(ticket);
        return routingCommands.routeTicketStatus(ticket);
    }

    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket, MutationRunner runner) {
        requireTicket(ticket);
        requireRunner(runner);
        return runner.mutate("route.session.publish", () -> routingCommands.publishRouteSession(ticket));
    }

    private CompletableFuture<RouteClearView> clearRouteBody(RouteTicket ticket, String reason, MutationRunner runner) {
        requireTicket(ticket);
        requireRunner(runner);
        String normalizedReason = reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason;
        return runner.mutate("route.clear", () -> routingCommands.clearRoute(ticket, normalizedReason));
    }

    public CompletableFuture<RouteClearResult> clearRouteAction(RouteTicket ticket, String reason, MutationRunner runner) {
        return clearRouteBody(ticket, reason, runner).thenApply(IslandRoutingUseCase::routeClearResult);
    }

    private static RouteClearResult routeClearResult(RouteClearView view) {
        return new RouteClearResult(view.accepted(), view.code());
    }

    private static void requireTicket(RouteTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket is required");
        }
        requireUuid(ticket.ticketId(), "ticketId");
        requireUuid(ticket.playerUuid(), "playerUuid");
    }

    private static void requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record RouteClearResult(boolean accepted, String code) {
        public RouteClearResult {
            code = code == null ? "" : code;
        }
    }
}
