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

final class IslandHomeWarpRuntimeAdapter implements IslandHomeWarpCommandHandler.Runtime {
    private final IslandCommandRuntimeServices runtime;
    private final IslandRoutingCommandHandler routingCommands;

    IslandHomeWarpRuntimeAdapter(IslandCommandRuntimeServices runtime, IslandRoutingCommandHandler routingCommands) {
        this.runtime = runtime;
        this.routingCommands = routingCommands;
    }

    @Override
    public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        return runtime.currentIsland(player, missingMessage);
    }

    @Override
    public boolean allowed(Player player, IslandPermission permission) {
        return runtime.allowed(player, permission);
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
    public IslandLocation location(Location location) {
        return runtime.location(location);
    }

    @Override
    public void moveToPoint(Player player, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
        runtime.moveToPoint(player, point, missingMessage, successMessage);
    }

    @Override
    public boolean teleportLocalDefaultHome(Player player) {
        return runtime.teleportLocalDefaultHome(player);
    }

    @Override
    public boolean coreUnavailable(Throwable error) {
        return runtime.coreUnavailable(error);
    }

    @Override
    public boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
        return runtime.publicWarpAllowed(player, point, islandPublicAccess);
    }

    @Override
    public void routeWarp(Player player, UUID islandId, String warpName) {
        routingCommands.routeWarp(player, islandId, warpName);
    }

    @Override
    public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
        runtime.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
    }

    @Override
    public boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
        return runtime.confirmationAccepted(player, action, click);
    }
}
