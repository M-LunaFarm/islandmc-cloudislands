package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandCommandPlayerResolver {
    private final Plugin plugin;
    private final MemberManagementUseCase memberManagement;

    IslandCommandPlayerResolver(Plugin plugin, MemberManagementUseCase memberManagement) {
        this.plugin = plugin;
        this.memberManagement = memberManagement;
    }

    CompletableFuture<UUID> resolvePlayerUuid(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        UUID parsed = uuid(value);
        if (parsed != null) {
            return CompletableFuture.completedFuture(parsed);
        }
        return memberManagement.playerUuidByName(value)
            .thenApply(profileUuid -> {
                if (profileUuid == null) {
                    throw new IllegalArgumentException("player profile was not found: " + value);
                }
                return profileUuid;
            });
    }

    private UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
