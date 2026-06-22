package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class IslandCommandRuntimeServices {
    private final IslandCommandMessenger commandMessages;
    private final IslandCommandIslandContext islandContext;
    private final IslandCommandLocalTeleports localTeleports;
    private final IslandCommandConfirmations confirmations;
    private final IslandCommandPlayerResolver playerResolver;

    IslandCommandRuntimeServices(
        IslandCommandMessenger commandMessages,
        IslandCommandIslandContext islandContext,
        IslandCommandLocalTeleports localTeleports,
        IslandCommandConfirmations confirmations,
        IslandCommandPlayerResolver playerResolver
    ) {
        this.commandMessages = commandMessages;
        this.islandContext = islandContext;
        this.localTeleports = localTeleports;
        this.confirmations = confirmations;
        this.playerResolver = playerResolver;
    }

    <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return IslandCommandRuntimeSupport.mutate(auditAction, operation);
    }

    <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return IslandCommandRuntimeSupport.mutateIdempotent(auditAction, operation);
    }

    void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
        confirmations.open(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
    }

    boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
        return confirmations.accepted(player, action, click);
    }

    String playerCodeMessage(String code, String fallback) {
        return commandMessages.playerCodeMessage(code, fallback);
    }

    MessageRenderer messagesFor(Player player) {
        return commandMessages.messagesFor(player);
    }

    String routeMessage(String key, String fallback, String... variables) {
        return commandMessages.routeMessage(key, fallback, variables);
    }

    String routeMessage(Player player, String key, String fallback, String... variables) {
        return commandMessages.routeMessage(player, key, fallback, variables);
    }

    java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        return islandContext.currentIsland(player, missingMessage);
    }

    boolean allowed(Player player, IslandPermission permission) {
        return islandContext.allowed(player, permission);
    }

    boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
        return islandContext.publicWarpAllowed(player, point, islandPublicAccess);
    }

    IslandLocation location(Location location) {
        return islandContext.location(location);
    }

    String joined(String[] args, int start) {
        return IslandCommandArgs.joined(args, start);
    }

    int integer(String value, int fallback) {
        return IslandCommandArgs.integer(value, fallback);
    }

    long longValue(String value, long fallback) {
        return IslandCommandArgs.longValue(value, fallback);
    }

    CompletableFuture<UUID> resolvePlayerUuid(String value) {
        return playerResolver.resolvePlayerUuid(value);
    }

    void moveToPoint(Player player, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
        localTeleports.moveToPoint(player, point, missingMessage, successMessage);
    }

    boolean teleportLocalDefaultHome(Player player) {
        return localTeleports.teleportLocalDefaultHome(player);
    }

    void message(Player player, String message) {
        commandMessages.message(player, message);
    }

    String playerMessage(String message) {
        return commandMessages.playerMessage(message);
    }

    String coreWriteFailureMessage(Throwable error, String fallback) {
        return IslandCommandRuntimeSupport.coreWriteFailureMessage(
            coreUnavailable(error),
            routeMessage("core-service-maintenance", IslandCommandRuntimeSupport.maintenanceFallback()),
            fallback
        );
    }

    boolean coreUnavailable(Throwable error) {
        return IslandCommandRuntimeSupport.coreUnavailable(error);
    }
}
