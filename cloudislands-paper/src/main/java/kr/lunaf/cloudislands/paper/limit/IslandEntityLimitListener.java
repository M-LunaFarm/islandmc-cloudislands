package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class IslandEntityLimitListener implements Listener {
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final Map<UUID, Long> observedEntities = new ConcurrentHashMap<>();

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
                return;
            }
            observedEntities.merge(islandId, 1L, Long::sum);
        });
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> observedEntities.merge(islandId, -1L, (left, right) -> Math.max(0L, left + right)));
    }
}
