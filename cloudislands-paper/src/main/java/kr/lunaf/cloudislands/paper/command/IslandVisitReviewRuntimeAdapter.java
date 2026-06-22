package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;

final class IslandVisitReviewRuntimeAdapter implements IslandVisitReviewCommandHandler.Runtime {
    private final IslandCommandRuntimeServices runtime;
    private final IslandRoutingCommandHandler routingCommands;

    IslandVisitReviewRuntimeAdapter(IslandCommandRuntimeServices runtime, IslandRoutingCommandHandler routingCommands) {
        this.runtime = runtime;
        this.routingCommands = routingCommands;
    }

    @Override
    public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        return runtime.currentIsland(player, missingMessage);
    }

    @Override
    public void message(Player player, String message) {
        runtime.message(player, message);
    }

    @Override
    public String routeMessage(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
    }

    @Override
    public String playerCodeMessage(String code, String fallback) {
        return runtime.playerCodeMessage(code, fallback);
    }

    @Override
    public String coreWriteFailureMessage(Throwable error, String fallback) {
        return runtime.coreWriteFailureMessage(error, fallback);
    }

    @Override
    public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return runtime.mutate(auditAction, operation);
    }

    @Override
    public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return runtime.mutateIdempotent(auditAction, operation);
    }

    @Override
    public MessageRenderer messagesFor(Player player) {
        return runtime.messagesFor(player);
    }

    @Override
    public void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        routingCommands.routeTicket(player, ticketFuture, failureMessage);
    }
}
