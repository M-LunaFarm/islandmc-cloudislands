package kr.lunaf.cloudislands.paper.activation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class EmptyIslandSaveTask {
    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final ProtectionController protection;
    private final IslandSaveService saveService;
    private final Map<UUID, Long> emptySinceMillis = new HashMap<>();
    private final Set<UUID> savedWhileEmpty = new HashSet<>();
    private BukkitTask task;
    private long delayMillis;

    public EmptyIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, ProtectionController protection, IslandSaveService saveService) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.protection = protection;
        this.saveService = saveService;
    }

    public void start(long delaySeconds) {
        stop();
        if (delaySeconds <= 0L) {
            return;
        }
        this.delayMillis = delaySeconds * 1000L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        emptySinceMillis.clear();
        savedWhileEmpty.clear();
    }

    private void scan() {
        Set<UUID> occupied = occupiedIslands();
        long now = System.currentTimeMillis();
        for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands.snapshot()) {
            UUID islandId = activeIsland.islandId();
            if (occupied.contains(islandId)) {
                emptySinceMillis.remove(islandId);
                savedWhileEmpty.remove(islandId);
                continue;
            }
            long emptySince = emptySinceMillis.computeIfAbsent(islandId, ignored -> now);
            if (now - emptySince >= delayMillis && savedWhileEmpty.add(islandId)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveEmptyIsland(activeIsland));
            }
        }
        emptySinceMillis.keySet().removeIf(islandId -> activeIslands.find(islandId).isEmpty());
        savedWhileEmpty.removeIf(islandId -> activeIslands.find(islandId).isEmpty());
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
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Empty island save failed for " + activeIsland.islandId() + ": " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> savedWhileEmpty.remove(activeIsland.islandId()));
        }
    }
}
