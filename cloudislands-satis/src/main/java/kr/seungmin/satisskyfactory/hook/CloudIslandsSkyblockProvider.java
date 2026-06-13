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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class CloudIslandsSkyblockProvider implements SkyblockProvider {
    private final JavaPlugin plugin;
    private CloudIslandsApi api;
    private boolean available;

    public CloudIslandsSkyblockProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enable() {
        api = resolveCloudIslandsApi();
        available = api != null;
        if (!available && plugin != null) {
            plugin.getLogger().severe("CloudIslands provider selected, but CloudIslands API was not found.");
        }
        return available;
    }

    private CloudIslandsApi resolveCloudIslandsApi() {
        CloudIslandsApi provider = CloudIslandsProvider.get().orElse(null);
        if (provider != null) {
            return provider;
        }
        if (plugin == null) {
            return null;
        }
        return plugin.getServer().getServicesManager().load(CloudIslandsApi.class);
    }

    @Override
    public void configure(boolean allowCoopBuild, boolean protectSpawnIsland, boolean requireIslandMember) {
    }

    @Override
    public Optional<IslandRef> getIslandAt(Location location) {
        if (!available || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return joinOptional(() -> api.islands().getIslandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                .flatMap(this::ref);
    }

    @Override
    public Optional<IslandRef> getIslandOf(Player player) {
        if (!available || player == null) {
            return Optional.empty();
        }
        Optional<IslandSnapshot> ownedIsland = joinOptional(() -> api.islands().getIslandByOwner(player.getUniqueId()));
        if (ownedIsland.isPresent()) {
            return ownedIsland.flatMap(this::ref);
        }
        return join(() -> api.players().getJoinedIslands(player.getUniqueId()))
                .flatMap(islands -> islands.stream().findFirst())
                .flatMap(this::ref);
    }

    @Override
    public Optional<IslandRef> getIslandByUuid(UUID islandUuid) {
        if (!available || islandUuid == null) {
            return Optional.empty();
        }
        return joinOptional(() -> api.islands().getIsland(islandUuid)).flatMap(this::ref);
    }

    @Override
    public Optional<Location> getIslandCenter(IslandRef island) {
        if (!available || island == null || plugin == null) {
            return Optional.empty();
        }
        return joinOptional(() -> api.islands().getRegion(island.islandUuid()))
                .flatMap(region -> {
                    if (region.worldName() == null || region.worldName().isBlank()) {
                        return Optional.empty();
                    }
                    World world = plugin.getServer().getWorld(region.worldName());
                    if (world == null) {
                        return Optional.empty();
                    }
                    return Optional.of(center(region, world));
                });
    }

    @Override
    public UUID getIslandUuid(IslandRef island) {
        return island == null ? null : island.islandUuid();
    }

    @Override
    public UUID getIslandOwnerUuid(IslandRef island) {
        return island == null ? null : island.ownerUuid();
    }

    @Override
    public boolean canBuildFactory(Player player, Location location) {
        if (!available || player == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (player.hasPermission("satisskyfactory.admin")) {
            return true;
        }
        return join(() -> api.permissions().checkAt(player.getUniqueId(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), IslandPermission.BUILD))
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
        if (!available || player == null || island == null) {
            return false;
        }
        if (player.hasPermission("satisskyfactory.admin") || player.getUniqueId().equals(island.ownerUuid())) {
            return true;
        }
        return join(() -> api.islands().getMembers(island.islandUuid()))
                .map(members -> member(members, player.getUniqueId()))
                .orElse(false);
    }

    private Optional<IslandRef> ref(IslandSnapshot island) {
        if (island == null || island.islandId() == null || island.ownerUuid() == null) {
            return Optional.empty();
        }
        return Optional.of(new IslandRef(island, island.islandId(), island.ownerUuid()));
    }

    private boolean member(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
                .anyMatch(member -> member != null
                        && member.role() != null
                        && playerUuid.equals(member.playerUuid())
                        && member.role().islandMemberRole());
    }

    private Location center(IslandRegionSnapshot region, World world) {
        return new Location(world, region.originX() + 0.5D, 100.0D, region.originZ() + 0.5D);
    }

    private <T> Optional<T> join(Supplier<CompletableFuture<T>> futureSupplier) {
        try {
            CompletableFuture<T> future = futureSupplier.get();
            if (future == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(future.join());
        } catch (CompletionException exception) {
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> joinOptional(Supplier<CompletableFuture<Optional<T>>> futureSupplier) {
        try {
            CompletableFuture<Optional<T>> future = futureSupplier.get();
            if (future == null) {
                return Optional.empty();
            }
            return future.join();
        } catch (CompletionException exception) {
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
