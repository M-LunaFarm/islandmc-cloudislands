package kr.lunaf.cloudislands.paper.application;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandRoutingUseCase {
    private final CoreApiClient coreApiClient;

    public IslandRoutingUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName, MutationRunner runner) {
        requireUuid(playerUuid, "playerUuid");
        requireUuid(islandId, "islandId");
        requireRunner(runner);
        return runner.mutate("route.ticket.warp", () -> coreApiClient.createWarpTicket(playerUuid, islandId, warpName == null ? "" : warpName));
    }

    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket) {
        requireTicket(ticket);
        return coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce());
    }

    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket, MutationRunner runner) {
        requireTicket(ticket);
        requireRunner(runner);
        return runner.mutate("route.session.publish", () -> coreApiClient.publishRouteSession(ticket));
    }

    private CompletableFuture<String> clearRouteBody(RouteTicket ticket, String reason, MutationRunner runner) {
        requireTicket(ticket);
        requireRunner(runner);
        String normalizedReason = reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason;
        return runner.mutate("route.clear", () -> coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), normalizedReason));
    }

    public CompletableFuture<RouteClearResult> clearRouteAction(RouteTicket ticket, String reason, MutationRunner runner) {
        return clearRouteBody(ticket, reason, runner).thenApply(IslandRoutingUseCase::routeClearResult);
    }

    private static RouteClearResult routeClearResult(String body) {
        String code = body == null || body.isBlank() ? "CLEARED" : body.trim();
        return new RouteClearResult(true, code);
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
