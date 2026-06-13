package kr.seungmin.satisskyfactory.hook;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRegionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public final class CloudIslandsSkyblockProvider implements SkyblockProvider {
    private final JavaPlugin plugin;
    private CloudIslandsApi api;
    private boolean available;

    public CloudIslandsSkyblockProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enable() {
        PluginManager plugins = plugin.getServer().getPluginManager();
        api = CloudIslandsProvider.get().orElse(null);
        available = plugins.getPlugin("CloudIslands") != null && api != null;
        if (!available) {
            plugin.getLogger().severe("CloudIslands provider selected, but CloudIslands API was not found.");
        }
        return available;
    }

    @Override
    public void configure(boolean allowCoopBuild, boolean protectSpawnIsland, boolean requireIslandMember) {
    }

    @Override
    public Optional<IslandRef> getIslandAt(Location location) {
        if (!available || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return joinOptional(api.islands().getIslandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                .map(this::ref);
    }

    @Override
    public Optional<IslandRef> getIslandOf(Player player) {
        if (!available || player == null) {
            return Optional.empty();
        }
        return joinOptional(api.islands().getIslandByOwner(player.getUniqueId())).map(this::ref);
    }

    @Override
    public Optional<IslandRef> getIslandByUuid(UUID islandUuid) {
        if (!available || islandUuid == null) {
            return Optional.empty();
        }
        return joinOptional(api.islands().getIsland(islandUuid)).map(this::ref);
    }

    @Override
    public Optional<Location> getIslandCenter(IslandRef island) {
        if (!available || island == null) {
            return Optional.empty();
        }
        return joinOptional(api.islands().getRegion(island.islandUuid()))
                .flatMap(region -> {
                    World world = plugin.getServer().getWorld(region.worldName());
                    if (world == null) {
                        return Optional.empty();
                    }
                    return Optional.of(center(region, world));
                });
    }

    @Override
    public UUID getIslandUuid(IslandRef island) {
        return island.islandUuid();
    }

    @Override
    public UUID getIslandOwnerUuid(IslandRef island) {
        return island.ownerUuid();
    }

    @Override
    public boolean canBuildFactory(Player player, Location location) {
        if (!available || player == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (player.hasPermission("satisskyfactory.admin")) {
            return true;
        }
        return join(api.permissions().checkAt(player.getUniqueId(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), IslandPermission.BUILD))
                .map(result -> result.allowed())
                .orElse(false);
    }

    @Override
    public boolean isLocationInsidePlayerIsland(Player player, Location location) {
        Optional<IslandRef> playerIsland = getIslandOf(player);
        Optional<IslandRef> locationIsland = getIslandAt(location);
        return playerIsland.isPresent()
                && locationIsland.isPresent()
                && playerIsland.get().islandUuid().equals(locationIsland.get().islandUuid());
    }

    @Override
    public boolean isPlayerIslandMember(Player player, IslandRef island) {
        if (player == null || island == null) {
            return false;
        }
        if (player.hasPermission("satisskyfactory.admin") || player.getUniqueId().equals(island.ownerUuid())) {
            return true;
        }
        return join(api.islands().getMembers(island.islandUuid()))
                .map(members -> member(members, player.getUniqueId()))
                .orElse(false);
    }

    private IslandRef ref(IslandSnapshot island) {
        return new IslandRef(island, island.islandId(), island.ownerUuid());
    }

    private boolean member(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
                .anyMatch(member -> playerUuid.equals(member.playerUuid()) && member.role().islandMemberRole());
    }

    private Location center(IslandRegionSnapshot region, World world) {
        return new Location(world, region.originX() + 0.5D, 100.0D, region.originZ() + 0.5D);
    }

    private <T> Optional<T> join(java.util.concurrent.CompletableFuture<T> future) {
        try {
            return Optional.ofNullable(future.join());
        } catch (CompletionException exception) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> joinOptional(java.util.concurrent.CompletableFuture<Optional<T>> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            return Optional.empty();
        }
    }
}
