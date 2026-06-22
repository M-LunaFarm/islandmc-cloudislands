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
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionActionResult;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionView;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
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

    void listIslandPermissions(Player player) {
        runtime.currentIsland(player, "섬 안에서만 권한을 확인할 수 있습니다.").ifPresent(islandId -> {
            permissionUseCase.listPermissionViews(islandId)
                .thenAccept(permissions -> runtime.message(player, permissionListMessage(permissions)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 권한을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    void listIslandRoles(Player player) {
        runtime.currentIsland(player, "섬 안에서만 역할을 확인할 수 있습니다.").ifPresent(islandId -> {
            permissionUseCase.listRoleViews(islandId)
                .thenAccept(roles -> runtime.message(player, roleListMessage(roles)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 역할을 불러오지 못했습니다.");
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
        runtime.currentIsland(player, "섬 안에서만 권한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandPermissionMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player), page, rolePage));
    }

    void openIslandRoleMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 역할 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandRoleMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        stageIslandPermission(player, roleName, permissionName, allowedValue, "");
    }

    void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue, String expectedVersion) {
        runtime.currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(_islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                runtime.message(player, runtime.routeMessage("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            PermissionManagementUseCase.PermissionChange change = new PermissionManagementUseCase.PermissionChange(roleKey, permission, allowed, expectedVersion);
            stagedPermissionChanges.computeIfAbsent(player.getUniqueId(), _uuid -> new ConcurrentHashMap<>()).put(change.key(), change);
            runtime.message(player, runtime.routeMessage("permission-stage-success-prefix", "권한 변경을 임시 저장했습니다. 저장 버튼을 눌러 반영하세요: ")
                + roleKey + ":" + permission.name() + "=" + allowed);
        });
    }

    void resetStagedIslandPermissions(Player player) {
        stagedPermissionChanges.remove(player.getUniqueId());
        runtime.message(player, runtime.routeMessage("permission-stage-reset", "임시 권한 변경을 취소했습니다."));
        openIslandPermissionMenu(player);
    }

    void saveStagedIslandPermissions(Player player) {
        Map<String, PermissionManagementUseCase.PermissionChange> staged = stagedPermissionChanges.getOrDefault(player.getUniqueId(), Map.of());
        if (staged.isEmpty()) {
            runtime.message(player, runtime.routeMessage("permission-stage-empty", "저장할 권한 변경이 없습니다."));
            return;
        }
        runtime.currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            List<PermissionManagementUseCase.PermissionChange> changes = new ArrayList<>(staged.values());
            GuiStateMenus.openSaving(plugin, player, runtime.messagesFor(player), runtime.routeMessage("permission-save-title", "권한 저장"));
            saveStagedChangesSequentially(islandId, player, changes)
                .thenAccept(_ignored -> {
                    stagedPermissionChanges.remove(player.getUniqueId());
                    kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        GuiStateMenus.openSuccess(plugin, player, runtime.messagesFor(player), runtime.routeMessage("permission-save-title", "권한 저장"), runtime.routeMessage("permission-save-success", "권한 변경을 저장했습니다."), "island.permissions.open");
                    });
                })
                .exceptionally(error -> {
                    kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        GuiStateMenus.openConflict(plugin, player, runtime.messagesFor(player), runtime.routeMessage("permission-save-title", "권한 저장"), runtime.coreWriteFailureMessage(error, runtime.routeMessage("permission-save-failed", "권한 변경을 저장하지 못했습니다.")), "island.permissions.save", "island.permissions.open");
                    });
                    return null;
                });
        });
    }

    private CompletableFuture<MutationResult<PermissionMatrixView>> saveStagedChangesSequentially(UUID islandId, Player player, List<PermissionManagementUseCase.PermissionChange> changes) {
        return permissionUseCase.saveSequentiallyTyped(islandId, player.getUniqueId(), changes, runtime::mutate);
    }

    void upsertIslandRole(Player player, String roleKey, int weight, String displayName) {
        runtime.currentIsland(player, "섬 안에서만 역할을 편집할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("role-edit-denied", "섬 역할을 편집할 권한이 없습니다."));
                return;
            }
            permissionUseCase.upsertRoleTyped(islandId, player.getUniqueId(), roleKey, weight, displayName, runtime::mutate)
                .thenAccept(result -> runtime.message(player, roleSavedMessage(result.value())))
                .exceptionally(error -> {
                    runtime.message(player, "섬 역할을 저장하지 못했습니다.");
                    return null;
                });
        });
    }

    void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click) {
        String roleKey = roleKey(roleName);
        if (!editableRoleKey(roleKey)) {
            runtime.message(player, runtime.routeMessage("input-role-invalid", "올바른 역할을 입력해주세요."));
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
        runtime.currentIsland(player, "섬 안에서만 역할을 초기화할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("role-reset-denied", "섬 역할을 초기화할 권한이 없습니다."));
                return;
            }
            permissionUseCase.resetRoleTyped(islandId, player.getUniqueId(), roleKey, runtime::mutateIdempotent)
                .thenAccept(result -> runtime.message(player, "섬 역할 초기화 완료: " + result.value().role()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 역할을 초기화하지 못했습니다.");
                    return null;
                });
        });
    }

    void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        runtime.currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                runtime.message(player, runtime.routeMessage("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            PermissionManagementUseCase.PermissionChange change = new PermissionManagementUseCase.PermissionChange(roleKey, permission, allowed, "");
            permissionUseCase.setPermissionAction(islandId, player.getUniqueId(), change, runtime::mutate)
                .thenAccept(result -> runtime.message(player, permissionActionMessage(result, "섬 권한 변경 완료: " + roleKey + ":" + permission.name() + "=" + allowed, "섬 권한을 변경하지 못했습니다.")))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 권한을 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue) {
        runtime.currentIsland(player, "섬 안에서만 권한 예외를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            IslandPermission permission = islandPermission(permissionName);
            if (permission == null) {
                runtime.message(player, runtime.routeMessage("input-permission-set-invalid", "올바른 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            runtime.resolvePlayerUuid(target).thenAccept(targetUuid -> {
                permissionUseCase.setPermissionOverrideAction(islandId, player.getUniqueId(), targetUuid, permission, allowed, runtime::mutate)
                    .thenAccept(result -> runtime.message(player, permissionActionMessage(result, "섬 권한 예외 변경 완료: " + compactId(targetUuid.toString()) + ":" + permission.name() + "=" + allowed, "섬 권한 예외를 변경하지 못했습니다.")))
                    .exceptionally(error -> {
                        runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 권한 예외를 변경하지 못했습니다."));
                        return null;
                    });
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
                entries.add(permission.role() + ":" + permission.permission() + "=" + (permission.allowed() ? "허용" : "거부"));
            } else if (!permission.playerUuid().isBlank()) {
                overrides.add(compactId(permission.playerUuid()) + ":" + permission.permission() + "=" + (permission.allowed() ? "허용" : "거부"));
            }
        }
        String base = entries.isEmpty() ? "섬 권한 규칙이 없습니다." : "섬 권한: " + String.join(", ", entries);
        return overrides.isEmpty() ? base : base + " / 예외: " + String.join(", ", overrides);
    }

    private String roleListMessage(List<RoleView> roles) {
        List<String> entries = roles.stream()
            .map(role -> role.role() + "(weight=" + role.weight() + ", name=" + role.displayName() + ")")
            .toList();
        return entries.isEmpty() ? "섬 커스텀 역할이 없습니다." : "섬 역할: " + String.join(", ", entries);
    }

    private String roleSavedMessage(RoleView role) {
        return "섬 역할 저장 완료: " + role.role() + " weight=" + role.weight() + " name=" + role.displayName();
    }

    private String permissionActionMessage(PermissionActionResult result, String successMessage, String failureMessage) {
        if (result.accepted()) {
            return successMessage;
        }
        return result.code().isBlank() ? failureMessage : failureMessage + " 사유=" + result.code();
    }

    private String compactId(String value) {
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
