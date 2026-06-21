package kr.lunaf.cloudislands.paper.cache;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreIslandQueryClient;
import kr.lunaf.cloudislands.coreclient.CorePermissionQueryClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.IslandQueryClient;
import kr.lunaf.cloudislands.coreclient.PermissionAssignmentView;
import kr.lunaf.cloudislands.coreclient.PermissionQueryClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PermissionCacheSyncService {
    private final Plugin plugin;
    private final IslandQueryClient islands;
    private final PermissionQueryClient permissions;
    private final IslandEnvironmentQueryClient environment;
    private final LocalIslandPermissionCache cache;

    public PermissionCacheSyncService(Plugin plugin, CoreApiClient client, LocalIslandPermissionCache cache) {
        this.plugin = plugin;
        this.islands = new CoreIslandQueryClient(client);
        this.permissions = new CorePermissionQueryClient(client);
        this.environment = client.environment();
        this.cache = cache;
    }

    public void sync(UUID islandId) {
        if (Bukkit.isPrimaryThread()) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> sync(islandId));
            return;
        }
        try {
            cache.invalidate(islandId);
            loadMembers(islandId, islands.listMembers(islandId).join());
            loadRoles(islandId, permissions.roles(islandId).join());
            loadRules(islandId, permissions.permissions(islandId).join());
            loadFlags(islandId, environment.flagValues(islandId).join());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to sync island permission cache for " + islandId + ": " + exception.getMessage());
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void invalidate(UUID islandId) {
        cache.invalidate(islandId);
    }

    private void loadMembers(UUID islandId, java.util.List<CoreGuiViews.MemberView> members) {
        for (CoreGuiViews.MemberView member : members == null ? java.util.List.<CoreGuiViews.MemberView>of() : members) {
            try {
                cache.putRoleKey(islandId, UUID.fromString(member.playerUuid()), roleKey(member.role(), IslandRole.VISITOR.name()));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRules(UUID islandId, java.util.List<PermissionAssignmentView> assignments) {
        for (PermissionAssignmentView assignment : assignments == null ? java.util.List.<PermissionAssignmentView>of() : assignments) {
            try {
                IslandPermission permission = IslandPermission.valueOf(assignment.permission());
                if (assignment.playerUuid() == null || assignment.playerUuid().isBlank()) {
                    cache.putRuleKey(islandId, roleKey(assignment.role(), IslandRole.VISITOR.name()), permission, assignment.allowed());
                } else {
                    cache.putPlayerOverride(islandId, UUID.fromString(assignment.playerUuid()), permission, assignment.allowed());
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadRoles(UUID islandId, java.util.List<CoreGuiViews.RoleView> roles) {
        for (CoreGuiViews.RoleView role : roles == null ? java.util.List.<CoreGuiViews.RoleView>of() : roles) {
            try {
                cache.putRoleDefinition(islandId, roleKey(role.role(), ""));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void loadFlags(UUID islandId, java.util.Map<IslandFlag, String> flags) {
        (flags == null ? java.util.Map.<IslandFlag, String>of() : flags).forEach((flag, value) -> cache.putFlag(islandId, flag, value));
    }

    private String roleKey(String roleKey, String fallback) {
        String value = roleKey == null ? "" : roleKey;
        if (value.isBlank()) {
            value = fallback;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
