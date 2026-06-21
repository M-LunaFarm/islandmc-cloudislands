package kr.lunaf.cloudislands.paper.activation;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.storage.StorageOutagePolicy;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class EmptyIslandSaveTask {
    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final ProtectionController protection;
    private final IslandSaveService saveService;
    private final CoreApiClient coreApiClient;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Long> emptySinceMillis = new HashMap<>();
    private final Set<UUID> savedWhileEmpty = new HashSet<>();
    private final Map<UUID, Integer> retryQueue = new ConcurrentHashMap<>();
    private final AtomicLong failuresTotal = new AtomicLong();
    private TaskHandle task;
    private long delayMillis;

    public EmptyIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, ProtectionController protection, IslandSaveService saveService, CoreApiClient coreApiClient) {
        this(plugin, activeIslands, protection, saveService, coreApiClient, new BukkitPlatformScheduler(plugin));
    }

    public EmptyIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, ProtectionController protection, IslandSaveService saveService, CoreApiClient coreApiClient, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.protection = protection;
        this.saveService = saveService;
        this.coreApiClient = coreApiClient;
        this.scheduler = scheduler == null ? new BukkitPlatformScheduler(plugin) : scheduler;
    }

    public void start(long delaySeconds) {
        stop();
        if (delaySeconds <= 0L) {
            return;
        }
        this.delayMillis = delaySeconds * 1000L;
        task = scheduler.repeatGlobal(Duration.ofSeconds(1), Duration.ofSeconds(1), this::scan);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        emptySinceMillis.clear();
        savedWhileEmpty.clear();
        retryQueue.clear();
    }

    private void scan() {
        Set<UUID> occupied = occupiedIslands();
        long now = System.currentTimeMillis();
        for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands.snapshot()) {
            UUID islandId = activeIsland.islandId();
            if (occupied.contains(islandId)) {
                emptySinceMillis.remove(islandId);
                savedWhileEmpty.remove(islandId);
                retryQueue.remove(islandId);
                continue;
            }
            long emptySince = emptySinceMillis.computeIfAbsent(islandId, ignored -> now);
            if (now - emptySince >= delayMillis && savedWhileEmpty.add(islandId)) {
                scheduler.runAsync(() -> saveEmptyIsland(activeIsland));
            }
        }
        emptySinceMillis.keySet().removeIf(islandId -> activeIslands.find(islandId).isEmpty());
        savedWhileEmpty.removeIf(islandId -> activeIslands.find(islandId).isEmpty());
        retryQueue.keySet().removeIf(islandId -> activeIslands.find(islandId).isEmpty());
    }

    private Set<UUID> occupiedIslands() {
        Set<UUID> occupied = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            protection.islandAt(player.getLocation().getBlock()).ifPresent(occupied::add);
        }
        return occupied;
    }

    private void saveEmptyIsland(ActiveIslandRegistry.ActiveIsland activeIsland) {
        try {
            saveService.save(activeIsland.islandId(), activeIsland);
            retryQueue.remove(activeIsland.islandId());
            coreApiClient.lifecycle().deactivateIsland(activeIsland.islandId()).exceptionally(error -> {
                plugin.getLogger().warning("Empty island deactivate request failed for " + activeIsland.islandId());
                scheduler.runGlobal(() -> savedWhileEmpty.remove(activeIsland.islandId()));
                return null;
            });
        } catch (java.io.IOException exception) {
            failuresTotal.incrementAndGet();
            int attempts = retryQueue.merge(activeIsland.islandId(), 1, Integer::sum);
            plugin.getLogger().warning("Empty island save failed for " + activeIsland.islandId() + " retry=" + attempts + " queued=" + retryQueue.size() + " policy=" + StorageOutagePolicy.DEACTIVATION_POLICY + ": " + exception.getMessage());
            scheduler.runGlobal(() -> savedWhileEmpty.remove(activeIsland.islandId()));
        }
    }

    public int retryQueueSize() {
        return retryQueue.size();
    }

    public long failuresTotal() {
        return failuresTotal.get();
    }
}
