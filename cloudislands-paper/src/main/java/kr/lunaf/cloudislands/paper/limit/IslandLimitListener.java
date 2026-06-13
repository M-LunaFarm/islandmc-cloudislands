package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class IslandLimitListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final MessageRenderer messages;
    private final Map<UUID, Map<String, Long>> observed = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> seeded = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLimitNotice = new ConcurrentHashMap<>();

    public IslandLimitListener(ProtectionController protection, IslandLimitCache limits) {
        this(protection, limits, null);
    }

    public IslandLimitListener(ProtectionController protection, IslandLimitCache limits, MessageRenderer messages) {
        this.protection = protection;
        this.limits = limits;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String key = limitKey(event.getBlockPlaced().getType());
        if (key == null) {
            return;
        }
        protection.regionAt(event.getBlockPlaced()).ifPresent(region -> {
            UUID islandId = region.islandId();
            seedObserved(event.getBlockPlaced().getWorld(), region, key, event.getBlockPlaced());
            long limit = limits.limit(islandId, key, Long.MAX_VALUE);
            long current = count(islandId, key);
            if (current >= limit) {
                event.setCancelled(true);
                notifyLimit(event.getPlayer(), islandId, key, current, limit);
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
        protection.regionAt(event.getBlock()).ifPresent(region -> {
            seedObserved(event.getBlock().getWorld(), region, key, null);
            observed.computeIfAbsent(region.islandId(), ignored -> new ConcurrentHashMap<>()).merge(key, -1L, (left, right) -> Math.max(0L, left + right));
        });
    }

    private long count(UUID islandId, String key) {
        return observed.getOrDefault(islandId, Map.of()).getOrDefault(key, 0L);
    }

    private void notifyLimit(org.bukkit.entity.Player player, UUID islandId, String key, long current, long limit) {
        String noticeKey = islandId + ":" + key;
        long now = System.currentTimeMillis();
        long previous = lastLimitNotice.getOrDefault(noticeKey, 0L);
        if (now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastLimitNotice.put(noticeKey, now);
        player.sendMessage(message("limit-reached", "섬 {limit} 제한에 도달했습니다. 현재 {current}/{max}",
            "limit", limitName(key),
            "current", Long.toString(current),
            "max", Long.toString(limit)
        ));
    }

    private String message(String key, String fallback, String... variables) {
        if (messages == null) {
            return render(fallback, variables);
        }
        String rendered = messages.plain(key, variables);
        return rendered.isBlank() ? render(fallback, variables) : rendered;
    }

    private String render(String template, String... variables) {
        String rendered = template == null ? "" : template;
        for (int index = 0; index + 1 < variables.length; index += 2) {
            rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
        }
        return rendered;
    }

    private void seedObserved(World world, IslandRegion region, String key, Block excludedBlock) {
        Set<String> seededKeys = seeded.computeIfAbsent(region.islandId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!seededKeys.add(key)) {
            return;
        }
        long count = countLoadedBlocks(world, region, key, excludedBlock);
        observed.computeIfAbsent(region.islandId(), ignored -> new ConcurrentHashMap<>()).put(key, count);
    }

    private long countLoadedBlocks(World world, IslandRegion region, String key, Block excludedBlock) {
        long count = 0L;
        for (Chunk chunk : world.getLoadedChunks()) {
            int chunkMinX = chunk.getX() << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = chunk.getZ() << 4;
            int chunkMaxZ = chunkMinZ + 15;
            if (chunkMaxX < region.minX() || chunkMinX > region.maxX() || chunkMaxZ < region.minZ() || chunkMinZ > region.maxZ()) {
                continue;
            }
            int minX = Math.max(chunkMinX, region.minX());
            int maxX = Math.min(chunkMaxX, region.maxX());
            int minZ = Math.max(chunkMinZ, region.minZ());
            int maxZ = Math.min(chunkMaxZ, region.maxZ());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        if (excludedBlock != null && excludedBlock.getX() == x && excludedBlock.getY() == y && excludedBlock.getZ() == z) {
                            continue;
                        }
                        if (key.equals(limitKey(world.getBlockAt(x, y, z).getType()))) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
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
