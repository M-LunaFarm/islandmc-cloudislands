package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;

public final class IslandEntityLimitListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final Map<UUID, Long> observedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLimitNotice = new ConcurrentHashMap<>();

    public IslandEntityLimitListener(ProtectionController protection, IslandLimitCache limits) {
        this.protection = protection;
        this.limits = limits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        protection.islandAt(event.getLocation().getBlock()).ifPresent(islandId -> {
            long limit = limits.limit(islandId, "ENTITY", Long.MAX_VALUE);
            long current = observedEntities.getOrDefault(islandId, 0L);
            if (current >= limit) {
                event.setCancelled(true);
                notifyNearby(event, islandId, current, limit);
                return;
            }
            observedEntities.merge(islandId, 1L, Long::sum);
        });
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> observedEntities.merge(islandId, -1L, (left, right) -> Math.max(0L, left + right)));
    }

    private void notifyNearby(CreatureSpawnEvent event, UUID islandId, long current, long limit) {
        long now = System.currentTimeMillis();
        long previous = lastLimitNotice.getOrDefault(islandId, 0L);
        if (now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastLimitNotice.put(islandId, now);
        String message = "섬 엔티티 제한에 도달했습니다. 현재 " + current + "/" + limit + " (limitKey=ENTITY)";
        event.getLocation().getWorld().getPlayers().stream()
            .filter(player -> player.getLocation().getWorld().equals(event.getLocation().getWorld()))
            .filter(player -> player.getLocation().distanceSquared(event.getLocation()) <= 256.0D)
            .forEach(player -> player.sendMessage(message));
    }
}
