package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.hook.IslandRef;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MaintenanceTickService {
    private final JavaPlugin plugin;
    private final FactoryIslandService islands;
    private final SkyblockProvider skyblock;
    private final MaintenanceService maintenance;
    private final BooleanSupplier active;
    private final Predicate<UUID> islandTickReady;
    private BukkitTask task;

    public MaintenanceTickService(JavaPlugin plugin, FactoryIslandService islands, SkyblockProvider skyblock,
                                  MaintenanceService maintenance, BooleanSupplier active, Predicate<UUID> islandTickReady) {
        this.plugin = plugin;
        this.islands = islands;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
        this.active = active == null ? () -> true : active;
        this.islandTickReady = islandTickReady == null ? _island -> true : islandTickReady;
    }

    public void start(long intervalTicks) {
        stop();
        task = SchedulerUtil.repeating(plugin, this::tick, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean running() {
        return task != null;
    }

    private void tick() {
        if (!activeEnabled()) {
            return;
        }
        for (FactoryIsland island : islands.cached()) {
            if (!islandTickReady(island.islandUuid())) {
                continue;
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.ownerUuid());
            Object rawIsland = skyblock.getIslandByUuid(island.islandUuid())
                    .map(IslandRef::raw)
                    .orElse(null);
            long previousDebt = island.maintenanceDebt();
            MaintenanceStatus previousStatus = island.maintenanceStatus();
            long previousScore = island.factoryScore();
            long previousLastMaintenanceAt = island.lastMaintenanceAt();
            maintenance.chargeIfDue(island, owner, rawIsland);
            if (!islands.save(island)) {
                island.maintenanceDebt(previousDebt);
                island.maintenanceStatus(previousStatus);
                island.factoryScore(previousScore);
                island.lastMaintenanceAt(previousLastMaintenanceAt);
            }
        }
    }

    private boolean activeEnabled() {
        try {
            return active.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean islandTickReady(UUID islandId) {
        try {
            return islandTickReady.test(islandId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
