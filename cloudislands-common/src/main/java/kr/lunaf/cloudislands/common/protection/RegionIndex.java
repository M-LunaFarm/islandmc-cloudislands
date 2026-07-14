package kr.lunaf.cloudislands.common.protection;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.StampedLock;

public final class RegionIndex {
    private final Map<ChunkKey, List<IslandRegion>> regionsByChunk = new ConcurrentHashMap<>();
    private final StampedLock lock = new StampedLock();

    public void add(IslandRegion region) {
        long stamp = lock.writeLock();
        try {
            addUnlocked(region);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public void replaceIsland(IslandRegion region) {
        long stamp = lock.writeLock();
        try {
            removeIslandUnlocked(region.islandId());
            addUnlocked(region);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private void addUnlocked(IslandRegion region) {
        int minChunkX = Math.floorDiv(region.minX(), 16);
        int maxChunkX = Math.floorDiv(region.maxX(), 16);
        int minChunkZ = Math.floorDiv(region.minZ(), 16);
        int maxChunkZ = Math.floorDiv(region.maxZ(), 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                regionsByChunk.computeIfAbsent(
                    new ChunkKey(region.world(), chunkX, chunkZ),
                    ignored -> new CopyOnWriteArrayList<>()
                ).add(region);
            }
        }
    }

    public void removeIsland(UUID islandId) {
        long stamp = lock.writeLock();
        try {
            removeIslandUnlocked(islandId);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private void removeIslandUnlocked(UUID islandId) {
        for (List<IslandRegion> regions : regionsByChunk.values()) {
            regions.removeIf(region -> region.islandId().equals(islandId));
        }
        regionsByChunk.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public Optional<IslandRegion> find(String world, int blockX, int blockZ) {
        long stamp = lock.tryOptimisticRead();
        Optional<IslandRegion> result = findUnlocked(world, blockX, blockZ);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = findUnlocked(world, blockX, blockZ);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    private Optional<IslandRegion> findUnlocked(String world, int blockX, int blockZ) {
        return regionsByChunk.getOrDefault(ChunkKey.fromBlock(world, blockX, blockZ), List.of()).stream()
            .filter(region -> region.contains(world, blockX, blockZ))
            .findFirst();
    }

    public Optional<IslandRegion> findIsland(UUID islandId) {
        long stamp = lock.tryOptimisticRead();
        Optional<IslandRegion> result = findIslandUnlocked(islandId);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = findIslandUnlocked(islandId);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    private Optional<IslandRegion> findIslandUnlocked(UUID islandId) {
        return regionsByChunk.values().stream()
            .flatMap(List::stream)
            .filter(region -> region.islandId().equals(islandId))
            .findFirst();
    }

    public int indexedChunkCount() {
        long stamp = lock.tryOptimisticRead();
        int size = regionsByChunk.size();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                size = regionsByChunk.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return size;
    }

    public int indexedIslandCount() {
        long stamp = lock.readLock();
        try {
            return indexedIslandCountUnlocked();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    private int indexedIslandCountUnlocked() {
        Set<UUID> islands = new HashSet<>();
        regionsByChunk.values().forEach(regions -> regions.forEach(region -> islands.add(region.islandId())));
        return islands.size();
    }
}
