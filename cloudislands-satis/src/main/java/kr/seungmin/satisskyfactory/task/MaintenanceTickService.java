package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.hook.IslandRef;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.BooleanSupplier;

public final class MaintenanceTickService {
    private final JavaPlugin plugin;
    private final FactoryIslandService islands;
    private final SkyblockProvider skyblock;
    private final MaintenanceService maintenance;
    private final BooleanSupplier active;
    private BukkitTask task;

    public MaintenanceTickService(JavaPlugin plugin, FactoryIslandService islands, SkyblockProvider skyblock,
                                  MaintenanceService maintenance, BooleanSupplier active) {
        this.plugin = plugin;
        this.islands = islands;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
        this.active = active == null ? () -> true : active;
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
        if (!active.getAsBoolean()) {
            return;
        }
        for (FactoryIsland island : islands.cached()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(island.ownerUuid());
            Object rawIsland = skyblock.getIslandByUuid(island.islandUuid())
                    .map(IslandRef::raw)
                    .orElse(null);
            maintenance.chargeIfDue(island, owner, rawIsland);
            islands.save(island);
        }
    }
}
