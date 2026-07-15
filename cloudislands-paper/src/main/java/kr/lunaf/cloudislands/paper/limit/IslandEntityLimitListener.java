package kr.lunaf.cloudislands.paper.limit;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.integration.stacker.StackAmountService;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;

public final class IslandEntityLimitListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final MessageRenderer messages;
    private final IslandLevelScanService levelScanService;
    private final StackAmountService stackAmounts;
    private final Map<String, Long> lastLimitNotice = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public IslandEntityLimitListener(ProtectionController protection, IslandLimitCache limits) {
        this(protection, limits, null, null, StackAmountService.physicalOnly());
    }

    public IslandEntityLimitListener(ProtectionController protection, IslandLimitCache limits, MessageRenderer messages) {
        this(protection, limits, messages, null, StackAmountService.physicalOnly());
    }

    public IslandEntityLimitListener(
        ProtectionController protection,
        IslandLimitCache limits,
        MessageRenderer messages,
        IslandLevelScanService levelScanService
    ) {
        this(protection, limits, messages, levelScanService, StackAmountService.physicalOnly());
    }

    public IslandEntityLimitListener(
        ProtectionController protection,
        IslandLimitCache limits,
        MessageRenderer messages,
        IslandLevelScanService levelScanService,
        StackAmountService stackAmounts
    ) {
        this.protection = protection;
        this.limits = limits;
        this.messages = messages;
        this.levelScanService = levelScanService;
        this.stackAmounts = stackAmounts == null ? StackAmountService.physicalOnly() : stackAmounts;
    }

    public void invalidate(UUID islandId) {
        String prefix = islandId + ":";
        lastLimitNotice.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Hanging || event.getEntity() instanceof Vehicle) {
            return;
        }
        if (!IslandEntityLimitKeys.counts(event.getEntity())) {
            return;
        }
        IslandRegion region = protection.regionAt(event.getLocation().getBlock()).orElse(null);
        if (region == null) {
            return;
        }
        UUID islandId = region.islandId();
        if (event instanceof CreatureSpawnEvent creatureSpawn
            && creatureSpawn.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
            && !spawnerSpawnAllowed(event.getLocation(), islandId)) {
            event.setCancelled(true);
            return;
        }
        long addition = event instanceof SpawnerSpawnEvent spawnerSpawn
            ? stackAmounts.spawnerSpawnAmount(spawnerSpawn.getSpawner())
            : 1L;
        if (!entitySpawnAllowed(event.getLocation(), islandId, event.getEntityType(), addition)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawnAccepted(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof Hanging) && !(event.getEntity() instanceof Vehicle)
            && IslandEntityLimitKeys.counts(event.getEntity())) {
            recordAcceptedDelta(event.getLocation(), stackAmounts.entityAmount(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        protection.regionAt(event.getEntity().getLocation().getBlock()).ifPresent(region -> {
            if (!entitySpawnAllowed(event.getEntity().getLocation(), region.islandId(), event.getEntity().getType(), 1L)) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlaceAccepted(HangingPlaceEvent event) {
        recordAcceptedDelta(event.getEntity().getLocation(), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        protection.regionAt(event.getVehicle().getLocation().getBlock()).ifPresent(region -> {
            if (!entitySpawnAllowed(event.getVehicle().getLocation(), region.islandId(), event.getVehicle().getType(), 1L)) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreateAccepted(VehicleCreateEvent event) {
        recordAcceptedDelta(event.getVehicle().getLocation(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroyAccepted(VehicleDestroyEvent event) {
        recordAcceptedDelta(event.getVehicle().getLocation(), -1L);
    }

    @EventHandler
    public void onEntityDeathDrops(EntityDeathEvent event) {
        if (event.isAsynchronous()) {
            return;
        }
        if (IslandEntityLimitKeys.counts(event.getEntity())) {
            protection.regionAt(event.getEntity().getLocation().getBlock()).ifPresent(region ->
                applyMobDropRate(region.islandId(), event.getDrops()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeathCount(EntityDeathEvent event) {
        if (event.isAsynchronous()) {
            return;
        }
        if (IslandEntityLimitKeys.counts(event.getEntity())) {
            recordAcceptedDelta(event.getEntity().getLocation(), -1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveEvent event) {
        if (EntityRemovalAccountingPolicy.records(event.getCause())
            && IslandEntityLimitKeys.counts(event.getEntity())) {
            recordAcceptedDelta(event.getEntity().getLocation(), -stackAmounts.entityAmount(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreakAccepted(HangingBreakEvent event) {
        recordAcceptedDelta(event.getEntity().getLocation(), -1L);
    }

    private boolean entitySpawnAllowed(Location location, UUID islandId, EntityType entityType, long addition) {
        if (!withinLimit(location, islandId, "ENTITY", addition)) {
            return false;
        }
        return withinLimit(location, islandId, IslandEntityLimitKeys.limitKey(entityType), addition);
    }

    private boolean withinLimit(Location location, UUID islandId, String limitKey, long addition) {
        OptionalLong resolvedLimit = limits.limitIfReady(islandId, limitKey, Long.MAX_VALUE);
        if (resolvedLimit.isEmpty()) {
            notifyLoading(location, islandId, limitKey);
            return false;
        }
        long limit = resolvedLimit.getAsLong();
        if (limit == Long.MAX_VALUE) {
            return true;
        }
        OptionalLong resolvedCount = limits.blockCountIfReady(islandId, limitKey);
        if (resolvedCount.isEmpty()) {
            notifyLoading(location, islandId, limitKey);
            return false;
        }
        long current = resolvedCount.getAsLong();
        long safeAddition = Math.max(1L, addition);
        if (current >= limit || safeAddition > limit - current) {
            notifyNearby(location, islandId, limitKey, current, limit);
            return false;
        }
        return true;
    }

    private boolean spawnerSpawnAllowed(Location location, UUID islandId) {
        OptionalLong resolved = limits.limitIfReady(islandId, "RATE:SPAWNER_RATES", 100L);
        if (resolved.isEmpty()) {
            notifyLoading(location, islandId, "RATE:SPAWNER_RATES");
            return false;
        }
        long percent = Math.max(0L, resolved.getAsLong());
        if (percent <= 0L) {
            return false;
        }
        return percent >= 100L || random.nextInt(100) < percent;
    }

    private void recordAcceptedDelta(Location location, long delta) {
        protection.regionAt(location.getBlock()).ifPresent(region -> {
            if (levelScanService != null) {
                levelScanService.recordBlockDelta(region.islandId(), IslandEntityLimitKeys.COUNT_KEY, delta);
            } else {
                limits.recordBlockDelta(region.islandId(), IslandEntityLimitKeys.COUNT_KEY, delta);
            }
        });
    }

    private void applyMobDropRate(UUID islandId, java.util.List<ItemStack> drops) {
        long percent = Math.max(0L, limits.limit(islandId, "RATE:MOB_DROPS", 100L));
        MobDropRateScaler.scale(drops, percent, random);
    }

    private void notifyNearby(Location location, UUID islandId, String limitKey, long current, long limit) {
        if (!claimNotice(islandId, limitKey)) {
            return;
        }
        String message = message("limit-reached", "섬 {limit} 제한에 도달했습니다. 현재 {current}/{max}",
            "limit", limitName(limitKey),
            "current", Long.toString(current),
            "max", Long.toString(limit)
        );
        notifyNearby(location, message);
    }

    private void notifyLoading(Location location, UUID islandId, String key) {
        if (!claimNotice(islandId, key + ":loading")) {
            return;
        }
        String name = key.equals("RATE:SPAWNER_RATES") ? "스포너 생성률" : limitName(key);
        notifyNearby(location, message("limit-data-loading", "섬 {limit} 한도 데이터를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.",
            "limit", name
        ));
    }

    private boolean claimNotice(UUID islandId, String key) {
        String noticeKey = islandId + ":" + key;
        long now = System.currentTimeMillis();
        long previous = lastLimitNotice.getOrDefault(noticeKey, 0L);
        if (now - previous < NOTICE_COOLDOWN_MILLIS) {
            return false;
        }
        lastLimitNotice.put(noticeKey, now);
        return true;
    }

    private void notifyNearby(Location location, String message) {
        location.getWorld().getPlayers().stream()
            .filter(player -> player.getLocation().getWorld().equals(location.getWorld()))
            .filter(player -> player.getLocation().distanceSquared(location) <= 256.0D)
            .forEach(player -> player.sendMessage(message));
    }

    private String limitName(String limitKey) {
        if (limitKey != null && limitKey.startsWith("ENTITY_TYPE:")) {
            return "엔티티 " + limitKey.substring("ENTITY_TYPE:".length()).toLowerCase(java.util.Locale.ROOT);
        }
        return "엔티티";
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
}
