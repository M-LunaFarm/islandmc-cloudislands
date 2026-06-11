package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class IslandLimitListener implements Listener {
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final Map<UUID, Map<String, Long>> observed = new ConcurrentHashMap<>();

    public IslandLimitListener(ProtectionController protection, IslandLimitCache limits) {
        this.protection = protection;
        this.limits = limits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String key = limitKey(event.getBlockPlaced().getType());
        if (key == null) {
            return;
        }
        protection.islandAt(event.getBlockPlaced()).ifPresent(islandId -> {
            long limit = limits.limit(islandId, key, Long.MAX_VALUE);
            long current = count(islandId, key);
            if (current >= limit) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("섬 " + key + " 제한에 도달했습니다. (" + limit + ")");
                return;
            }
            observed.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).merge(key, 1L, Long::sum);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String key = limitKey(event.getBlock().getType());
        if (key == null) {
            return;
        }
        protection.islandAt(event.getBlock()).ifPresent(islandId -> observed.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).merge(key, -1L, (left, right) -> Math.max(0L, left + right)));
    }

    private long count(UUID islandId, String key) {
        return observed.getOrDefault(islandId, Map.of()).getOrDefault(key, 0L);
    }

    private String limitKey(Material material) {
        if (material == Material.HOPPER) {
            return "HOPPER";
        }
        if (material == Material.SPAWNER) {
            return "SPAWNER";
        }
        if (isRedstone(material)) {
            return "REDSTONE";
        }
        return null;
    }

    private boolean isRedstone(Material material) {
        return material == Material.REDSTONE_WIRE
            || material == Material.REDSTONE_TORCH
            || material == Material.REPEATER
            || material == Material.COMPARATOR
            || material == Material.PISTON
            || material == Material.STICKY_PISTON
            || material == Material.OBSERVER
            || material == Material.DISPENSER
            || material == Material.DROPPER;
    }
}
