package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class IslandMembershipRuntimeAdapter implements IslandMembershipCommandHandler.Runtime {
    private final IslandCommandRuntimeServices runtime;
    private final IslandCommandMemberPresentation memberPresentation;
    private final IslandPermissionCommandHandler permissionCommands;

    IslandMembershipRuntimeAdapter(
        IslandCommandRuntimeServices runtime,
        IslandCommandMemberPresentation memberPresentation,
        IslandPermissionCommandHandler permissionCommands
    ) {
        this.runtime = runtime;
        this.memberPresentation = memberPresentation;
        this.permissionCommands = permissionCommands;
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
    public MessageRenderer messagesFor(Player player) {
        return runtime.messagesFor(player);
    }

    @Override
    public String joined(String[] args, int start) {
        return runtime.joined(args, start);
    }

    @Override
    public int integer(String value, int fallback) {
        return runtime.integer(value, fallback);
    }

    @Override
    public long longValue(String value, long fallback) {
        return runtime.longValue(value, fallback);
    }

    @Override
    public String roleKey(String value) {
        return permissionCommands.roleKey(value);
    }

    @Override
    public boolean editableRoleKey(String roleKey) {
        return permissionCommands.editableRoleKey(roleKey);
    }

    @Override
    public int defaultRoleWeight(String roleKey) {
        return permissionCommands.defaultRoleWeight(roleKey);
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
    public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return runtime.mutate(auditAction, operation);
    }

    @Override
    public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return runtime.mutateIdempotent(auditAction, operation);
    }

    @Override
    public CompletableFuture<UUID> resolvePlayerUuid(String value) {
        return runtime.resolvePlayerUuid(value);
    }

    @Override
    public boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
        return memberPresentation.moveVisitorToFallback(islandId, targetUuid, successMessage, failureMessage);
    }

    @Override
    public String playerMessage(String message) {
        return runtime.playerMessage(message);
    }

    @Override
    public void openIslandMemberMenu(Player player) {
        memberPresentation.openMemberMenu(player);
    }

    @Override
    public void openIslandMemberMenu(Player player, int page) {
        memberPresentation.openMemberMenu(player, page);
    }

    @Override
    public void openIslandBanMenu(Player player) {
        memberPresentation.openBanMenu(player);
    }

    @Override
    public void listIslandPermissions(Player player) {
        permissionCommands.listIslandPermissions(player);
    }

    @Override
    public void openIslandPermissionMenu(Player player) {
        permissionCommands.openIslandPermissionMenu(player);
    }

    @Override
    public void openIslandPermissionMenu(Player player, int page, int rolePage) {
        permissionCommands.openIslandPermissionMenu(player, page, rolePage);
    }

    @Override
    public void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        permissionCommands.stageIslandPermission(player, roleName, permissionName, allowedValue);
    }

    @Override
    public void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue, String expectedVersion) {
        permissionCommands.stageIslandPermission(player, roleName, permissionName, allowedValue, expectedVersion);
    }

    @Override
    public void resetStagedIslandPermissions(Player player) {
        permissionCommands.resetStagedIslandPermissions(player);
    }

    @Override
    public void saveStagedIslandPermissions(Player player) {
        permissionCommands.saveStagedIslandPermissions(player);
    }

    @Override
    public void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        permissionCommands.setIslandPermission(player, roleName, permissionName, allowedValue);
    }

    @Override
    public void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue) {
        permissionCommands.setIslandPermissionOverride(player, target, permissionName, allowedValue);
    }

    @Override
    public void openIslandRoleMenu(Player player) {
        permissionCommands.openIslandRoleMenu(player);
    }

    @Override
    public void listIslandRoles(Player player) {
        permissionCommands.listIslandRoles(player);
    }

    @Override
    public void upsertIslandRole(Player player, String roleKey, int weight, String displayName) {
        permissionCommands.upsertIslandRole(player, roleKey, weight, displayName);
    }

    @Override
    public void resetIslandRole(Player player, String roleKey) {
        permissionCommands.resetIslandRole(player, roleKey);
    }

    @Override
    public void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click) {
        permissionCommands.adjustIslandRoleWeight(player, roleName, weightValue, displayName, click);
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
