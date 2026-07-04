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

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CloudIslandsSkyblockProvider implements SkyblockProvider {
    private final JavaPlugin plugin;
    private final Map<LocationKey, IslandRef> islandAtCache = new ConcurrentHashMap<>();
    private final Map<UUID, IslandRef> playerIslandCache = new ConcurrentHashMap<>();
    private final Map<UUID, IslandRef> islandCache = new ConcurrentHashMap<>();
    private final Map<UUID, Location> islandCenterCache = new ConcurrentHashMap<>();
    private final Map<PermissionKey, Boolean> buildPermissionCache = new ConcurrentHashMap<>();
    private final Map<MemberKey, Boolean> memberCache = new ConcurrentHashMap<>();
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
        LocationKey key = LocationKey.of(location);
        return cached(key, islandAtCache, () -> api.islands().getIslandAt(key.world(), key.x(), key.y(), key.z()), this::ref);
    }

    @Override
    public Optional<IslandRef> getIslandOf(Player player) {
        if (!available || player == null) {
            return Optional.empty();
        }
        UUID playerId = player.getUniqueId();
        IslandRef cached = playerIslandCache.get(playerId);
        if (cached != null) {
            refreshPlayerIsland(playerId);
            return Optional.of(cached);
        }
        refreshPlayerIsland(playerId);
        return Optional.empty();
    }

    @Override
    public Optional<IslandRef> getIslandByUuid(UUID islandUuid) {
        if (!available || islandUuid == null) {
            return Optional.empty();
        }
        return cached(islandUuid, islandCache, () -> api.islands().getIsland(islandUuid), this::ref);
    }

    @Override
    public Optional<Location> getIslandCenter(IslandRef island) {
        if (!available || island == null || plugin == null) {
            return Optional.empty();
        }
        UUID islandId = island.islandUuid();
        return cached(islandId, islandCenterCache, () -> api.islands().getRegion(islandId), region -> {
            if (region.islandId() == null || !region.islandId().equals(islandId)) {
                return Optional.empty();
            }
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
        PermissionKey key = PermissionKey.of(player.getUniqueId(), location);
        Boolean cached = buildPermissionCache.get(key);
        refreshBuildPermission(key);
        return Boolean.TRUE.equals(cached);
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
        MemberKey key = new MemberKey(island.islandUuid(), player.getUniqueId());
        Boolean cached = memberCache.get(key);
        refreshMembers(key);
        return Boolean.TRUE.equals(cached);
    }

    private void refreshPlayerIsland(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CompletableFuture<Optional<IslandSnapshot>> owned = api.islands().getIslandByOwner(playerId);
        owned.thenCompose(ownedIsland -> {
            if (ownedIsland.isPresent()) {
                return CompletableFuture.completedFuture(ownedIsland);
            }
            return api.players().getJoinedIslands(playerId).thenApply(islands -> islands.stream().findFirst());
        }).thenAccept(island -> island.flatMap(this::ref).ifPresent(ref -> {
            playerIslandCache.put(playerId, ref);
            islandCache.put(ref.islandUuid(), ref);
        })).exceptionally(_error -> null);
    }

    private void refreshBuildPermission(PermissionKey key) {
        if (key == null) {
            return;
        }
        api.permissions().checkAt(key.playerId(), key.world(), key.x(), key.y(), key.z(), IslandPermission.BUILD)
                .thenAccept(result -> buildPermissionCache.put(key, result != null && result.allowed()))
                .exceptionally(_error -> null);
    }

    private void refreshMembers(MemberKey key) {
        if (key == null) {
            return;
        }
        api.islands().getMembers(key.islandId())
                .thenAccept(members -> memberCache.put(key, member(members, key.playerId())))
                .exceptionally(_error -> null);
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

    private <K, T, R> Optional<R> cached(K key, Map<K, R> cache, Supplier<CompletableFuture<Optional<T>>> futureSupplier,
                                         java.util.function.Function<T, Optional<R>> mapper) {
        R cached = cache.get(key);
        CompletableFuture<Optional<T>> future;
        try {
            future = futureSupplier.get();
        } catch (RuntimeException exception) {
            return Optional.ofNullable(cached);
        }
        if (future == null) {
            return Optional.ofNullable(cached);
        }
        future.thenAccept(value -> value.flatMap(mapper).ifPresent(mapped -> cache.put(key, mapped))).exceptionally(_error -> null);
        return Optional.ofNullable(cached);
    }

    private record LocationKey(String world, int x, int y, int z) {
        private static LocationKey of(Location location) {
            return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record PermissionKey(UUID playerId, String world, int x, int y, int z) {
        private static PermissionKey of(UUID playerId, Location location) {
            return new PermissionKey(playerId, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record MemberKey(UUID islandId, UUID playerId) {
    }
}
