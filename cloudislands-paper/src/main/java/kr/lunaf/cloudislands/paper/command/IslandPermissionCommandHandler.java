package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.RoleView;
import kr.lunaf.cloudislands.coreclient.MutationResult;
import kr.lunaf.cloudislands.coreclient.PermissionMatrixView;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionActionResult;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionView;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.gui.IslandPermissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRoleMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandPermissionCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final PermissionManagementUseCase permissionUseCase;
    private final Runtime runtime;
    private final Map<UUID, Map<String, PermissionManagementUseCase.PermissionChange>> stagedPermissionChanges = new ConcurrentHashMap<>();

    IslandPermissionCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.permissionUseCase = new PermissionManagementUseCase(coreApiClient);
        this.runtime = runtime;
    }

    void clearPlayerState(UUID playerUuid) {
        if (playerUuid != null) {
            stagedPermissionChanges.remove(playerUuid);
        }
    }

    void listIslandPermissions(Player player) {
        runtime.currentIsland(player, message("permission-list-island-required", "섬 안에서만 권한을 확인할 수 있습니다.")).ifPresent(islandId -> {
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            permissionUseCase.listPermissionViews(islandId)
                .thenAccept(permissions -> deliverMessage(playerSession, permissionListMessage(permissions)))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("permission-list-load-failed", "섬 권한을 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    void listIslandRoles(Player player) {
        runtime.currentIsland(player, message("role-list-island-required", "섬 안에서만 역할을 확인할 수 있습니다.")).ifPresent(islandId -> {
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            permissionUseCase.listRoleViews(islandId)
                .thenAccept(roles -> deliverMessage(playerSession, roleListMessage(roles)))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("role-list-load-failed", "섬 역할을 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    void openIslandPermissionMenu(Player player) {
        openIslandPermissionMenu(player, 0);
    }

    void openIslandPermissionMenu(Player player, int page) {
        openIslandPermissionMenu(player, page, 0);
    }

    void openIslandPermissionMenu(Player player, int page, int rolePage) {
        runtime.currentIsland(player, message("permission-menu-island-required", "섬 안에서만 권한 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandPermissionMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player), page, rolePage));
    }

    void openIslandRoleMenu(Player player) {
        runtime.currentIsland(player, message("role-menu-island-required", "섬 안에서만 역할 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandRoleMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        stageIslandPermission(player, roleName, permissionName, allowedValue, "");
    }

    void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue, String expectedVersion) {
        UUID actorUuid = player.getUniqueId();
        runtime.currentIsland(player, message("permission-change-island-required", "섬 안에서만 권한을 변경할 수 있습니다.")).ifPresent(_islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                runtime.message(player, message("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            PermissionManagementUseCase.PermissionChange change = new PermissionManagementUseCase.PermissionChange(roleKey, permission, allowed, expectedVersion);
            stagedPermissionChanges.computeIfAbsent(actorUuid, _uuid -> new ConcurrentHashMap<>()).put(change.key(), change);
            runtime.message(player, message("permission-stage-success-prefix", "권한 변경을 임시 저장했습니다. 저장 버튼을 눌러 반영하세요: ")
                + roleKey + ":" + permission.name() + "=" + allowed);
        });
    }

    void resetStagedIslandPermissions(Player player) {
        UUID actorUuid = player.getUniqueId();
        stagedPermissionChanges.remove(actorUuid);
        runtime.message(player, message("permission-stage-reset", "임시 권한 변경을 취소했습니다."));
        openIslandPermissionMenu(player);
    }

    void saveStagedIslandPermissions(Player player) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID actorUuid = playerSession.playerUuid();
        Map<String, PermissionManagementUseCase.PermissionChange> stagedSession = stagedPermissionChanges.getOrDefault(actorUuid, Map.of());
        if (stagedSession.isEmpty()) {
            runtime.message(player, message("permission-stage-empty", "저장할 권한 변경이 없습니다."));
            return;
        }
        runtime.currentIsland(player, message("permission-change-island-required", "섬 안에서만 권한을 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            List<PermissionManagementUseCase.PermissionChange> changes = new ArrayList<>(stagedSession.values());
            MessageRenderer messages = runtime.messagesFor(player);
            GuiSession guiSession = GuiStateMenus.openSaving(plugin, player, messages, message("permission-save-title", "권한 저장"));
            saveStagedChangesSequentially(islandId, actorUuid, changes)
                .thenAccept(_ignored -> {
                    clearSavedChanges(actorUuid, stagedSession, changes);
                    GuiStateMenus.openSuccess(plugin, playerSession.expectedPlayer(), guiSession, messages,
                        message("permission-save-title", "권한 저장"), message("permission-save-success", "권한 변경을 저장했습니다."), "island.permissions.open");
                })
                .exceptionally(error -> {
                    String detail = runtime.coreWriteFailureMessage(error, message("permission-save-failed", "권한 변경을 저장하지 못했습니다."));
                    GuiStateMenus.openConflict(plugin, playerSession.expectedPlayer(), guiSession, messages,
                        message("permission-save-title", "권한 저장"), detail, "island.permissions.save", "island.permissions.open");
                    return null;
                });
        });
    }

    private void clearSavedChanges(UUID actorUuid, Map<String, PermissionManagementUseCase.PermissionChange> stagedSession, List<PermissionManagementUseCase.PermissionChange> savedChanges) {
        stagedPermissionChanges.computeIfPresent(actorUuid, (_uuid, current) -> {
            StagedPermissionChangePolicy.removeSaved(current, stagedSession, savedChanges);
            return current.isEmpty() ? null : current;
        });
    }

    private CompletableFuture<MutationResult<PermissionMatrixView>> saveStagedChangesSequentially(UUID islandId, UUID actorUuid, List<PermissionManagementUseCase.PermissionChange> changes) {
        return permissionUseCase.saveSequentiallyTyped(islandId, actorUuid, changes, runtime::mutate);
    }

    void upsertIslandRole(Player player, String roleKey, int weight, String displayName) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID actorUuid = playerSession.playerUuid();
        runtime.currentIsland(player, message("role-edit-island-required", "섬 안에서만 역할을 편집할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("role-edit-denied", "섬 역할을 편집할 권한이 없습니다."));
                return;
            }
            permissionUseCase.upsertRoleTyped(islandId, actorUuid, roleKey, weight, displayName, runtime::mutate)
                .thenAccept(result -> deliverMessage(playerSession, roleSavedMessage(result.value())))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("role-save-failed", "섬 역할을 저장하지 못했습니다."));
                    return null;
                });
        });
    }

    void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click) {
        String roleKey = roleKey(roleName);
        if (!editableRoleKey(roleKey)) {
            runtime.message(player, message("input-role-invalid", "올바른 역할을 입력해주세요."));
            return;
        }
        if (click.shift()) {
            resetIslandRole(player, roleKey);
            return;
        }
        int currentWeight = (int) Math.max(0L, Math.min(100L, longValue(weightValue, 0L)));
        int updatedWeight = Math.max(0, Math.min(100, currentWeight + (click.right() ? -1 : 1)));
        upsertIslandRole(player, roleKey, updatedWeight, displayName);
    }

    void resetIslandRole(Player player, String roleKey) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID actorUuid = playerSession.playerUuid();
        runtime.currentIsland(player, message("role-reset-island-required", "섬 안에서만 역할을 초기화할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("role-reset-denied", "섬 역할을 초기화할 권한이 없습니다."));
                return;
            }
            permissionUseCase.resetRoleTyped(islandId, actorUuid, roleKey, runtime::mutateIdempotent)
                .thenAccept(result -> deliverMessage(playerSession, message("role-reset-success-prefix", "섬 역할 초기화 완료: ") + result.value().role()))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("role-reset-failed", "섬 역할을 초기화하지 못했습니다."));
                    return null;
                });
        });
    }

    void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID actorUuid = playerSession.playerUuid();
        runtime.currentIsland(player, message("permission-change-island-required", "섬 안에서만 권한을 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                runtime.message(player, message("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            PermissionManagementUseCase.PermissionChange change = new PermissionManagementUseCase.PermissionChange(roleKey, permission, allowed, "");
            permissionUseCase.setPermissionAction(islandId, actorUuid, change, runtime::mutate)
                .thenAccept(result -> deliverMessage(playerSession, permissionActionMessage(result, message("permission-change-success-prefix", "섬 권한 변경 완료: ") + roleKey + ":" + permission.name() + "=" + allowed, message("permission-change-failed", "섬 권한을 변경하지 못했습니다."))))
                .exceptionally(error -> {
                    deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("permission-change-failed", "섬 권한을 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID actorUuid = playerSession.playerUuid();
        runtime.currentIsland(player, message("permission-override-island-required", "섬 안에서만 권한 예외를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, message("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            IslandPermission permission = islandPermission(permissionName);
            if (permission == null) {
                runtime.message(player, message("input-permission-set-invalid", "올바른 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            runtime.resolvePlayerUuid(target)
                .thenCompose(targetUuid -> permissionUseCase.setPermissionOverrideAction(islandId, actorUuid, targetUuid, permission, allowed, runtime::mutate)
                    .thenAccept(result -> deliverMessage(playerSession, permissionActionMessage(result, message("permission-override-success-prefix", "섬 권한 예외 변경 완료: ") + compactId(targetUuid.toString()) + ":" + permission.name() + "=" + allowed, message("permission-override-failed", "섬 권한 예외를 변경하지 못했습니다.")))))
                .exceptionally(error -> {
                    deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("permission-override-failed", "섬 권한 예외를 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    String roleKey(String value) {
        return IslandRoleKeyPolicy.normalize(value);
    }

    boolean editableRoleKey(String roleKey) {
        return IslandRoleKeyPolicy.editable(roleKey);
    }

    int defaultRoleWeight(String roleKey) {
        return IslandRoleKeyPolicy.defaultWeight(roleKey);
    }

    private IslandPermission islandPermission(String value) {
        try {
            return IslandPermission.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean booleanValue(String value) {
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("yes")
            || value.equalsIgnoreCase("on")
            || value.equals("1")
            || value.equals("허용");
    }

    private long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String permissionListMessage(List<PermissionView> permissions) {
        List<String> entries = new ArrayList<>();
        List<String> overrides = new ArrayList<>();
        for (PermissionView permission : permissions) {
            if (!permission.role().isBlank()) {
                entries.add(permission.role() + ":" + permission.permission() + "=" + permissionAllowedLabel(permission.allowed()));
            } else if (!permission.playerUuid().isBlank()) {
                overrides.add(permissionDisplayName(permission) + ":" + permission.permission() + "=" + permissionAllowedLabel(permission.allowed()));
            }
        }
        String base = entries.isEmpty() ? message("permission-list-empty", "섬 권한 규칙이 없습니다.") : message("permission-list-prefix", "섬 권한: ") + String.join(", ", entries);
        return overrides.isEmpty() ? base : base + message("permission-list-overrides-prefix", " / 예외: ") + String.join(", ", overrides);
    }

    static String permissionDisplayName(PermissionView permission) {
        return permission.playerName().isBlank() ? compactId(permission.playerUuid()) : permission.playerName().trim();
    }

    private String roleListMessage(List<RoleView> roles) {
        List<String> entries = roles.stream()
            .map(role -> role.role() + "(" + message("role-list-weight-label", "weight=") + role.weight() + ", " + message("role-list-name-label", "name=") + role.displayName() + ")")
            .toList();
        return entries.isEmpty() ? message("role-list-empty", "섬 커스텀 역할이 없습니다.") : message("role-list-prefix", "섬 역할: ") + String.join(", ", entries);
    }

    private String roleSavedMessage(RoleView role) {
        return message("role-save-success-prefix", "섬 역할 저장 완료: ") + role.role()
            + " " + message("role-list-weight-label", "weight=") + role.weight()
            + " " + message("role-list-name-label", "name=") + role.displayName();
    }

    private String permissionActionMessage(PermissionActionResult result, String successMessage, String failureMessage) {
        if (result.accepted()) {
            return successMessage;
        }
        return result.code().isBlank() ? failureMessage : failureMessage + message("permission-action-reason-prefix", " 사유=") + result.code();
    }

    private String permissionAllowedLabel(boolean allowed) {
        return allowed ? message("permission-allowed-label", "허용") : message("permission-denied-label", "거부");
    }

    private void deliverMessage(PlayerConnectionSession playerSession, String detail) {
        PaperOnlinePlayer.run(plugin, playerSession.playerUuid(), activePlayer -> {
            if (playerSession.isCurrent(activePlayer)) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
    }

    private static String compactId(String value) {
        if (value == null || value.length() != 36 || !value.contains("-")) {
            return value;
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
    }

    interface Runtime {
        java.util.Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        MessageRenderer messagesFor(Player player);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        CompletableFuture<UUID> resolvePlayerUuid(String value);

        String coreWriteFailureMessage(Throwable error, String fallback);
    }
}
