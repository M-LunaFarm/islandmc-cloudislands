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
                event.getPlayer().sendMessage("섬 " + limitName(key) + " 제한에 도달했습니다. 현재 " + current + "/" + limit + " (limitKey=" + key + ")");
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

    private String limitName(String key) {
        return switch (key) {
            case "HOPPER" -> "호퍼";
            case "SPAWNER" -> "스포너";
            case "REDSTONE" -> "레드스톤";
            default -> key;
        };
    }

    private boolean isRedstone(Material material) {
        String name = material.name();
        return name.contains("REDSTONE")
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_PISTON")
            || name.endsWith("_RAIL")
            || material == Material.REPEATER
            || material == Material.COMPARATOR
            || material == Material.LEVER
            || material == Material.OBSERVER
            || material == Material.DISPENSER
            || material == Material.DROPPER
            || material == Material.DAYLIGHT_DETECTOR
            || material == Material.TRIPWIRE_HOOK
            || material == Material.TRAPPED_CHEST
            || material == Material.TARGET
            || material == Material.NOTE_BLOCK;
    }
}
