package kr.seungmin.satisskyfactory.listener;

import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.function.BooleanSupplier;

public final class FactoryLifecycleListener implements Listener {
    private final BooleanSupplier active;
    private final BooleanSupplier resourceNodesEnabled;
    private final BooleanSupplier machinesEnabled;
    private final BooleanSupplier maintenanceEnabled;
    private final FactoryIslandService islands;
    private final SkyblockProvider skyblock;
    private final ResourceNodeService nodes;
    private final MachineService machines;
    private final ItemNetworkService itemNetworks;
    private final PowerNetworkService power;
    private final MaintenanceService maintenance;

    public FactoryLifecycleListener(BooleanSupplier active, BooleanSupplier resourceNodesEnabled,
                                    BooleanSupplier machinesEnabled, BooleanSupplier maintenanceEnabled,
                                    FactoryIslandService islands, SkyblockProvider skyblock,
                                    ResourceNodeService nodes, MachineService machines, ItemNetworkService itemNetworks,
                                    PowerNetworkService power, MaintenanceService maintenance) {
        this.active = active;
        this.resourceNodesEnabled = resourceNodesEnabled;
        this.machinesEnabled = machinesEnabled;
        this.maintenanceEnabled = maintenanceEnabled;
        this.islands = islands;
        this.skyblock = skyblock;
        this.nodes = nodes;
        this.machines = machines;
        this.itemNetworks = itemNetworks;
        this.power = power;
        this.maintenance = maintenance;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!active.getAsBoolean() || !lifecycleDataWritesEnabled()) {
            return;
        }
        skyblock.getIslandOf(event.getPlayer()).ifPresent(islandRef -> {
            FactoryIsland island = islands.getOrCreate(islandRef);
            island.lastTickAt(System.currentTimeMillis());
            Location origin = skyblock.getIslandCenter(islandRef).orElse(event.getPlayer().getLocation());
            if (resourceNodesEnabled.getAsBoolean()) {
                nodes.generateIfMissing(island.islandUuid(), origin, location -> skyblock.getIslandAt(location)
                        .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                        .orElse(false));
            }
            if (maintenanceEnabled.getAsBoolean()) {
                maintenance.updateStatus(island);
            }
            if (machinesEnabled.getAsBoolean()) {
                itemNetworks.rebuildIsland(island.islandUuid());
                power.rebuildIsland(island.islandUuid());
            }
            islands.save(island);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!active.getAsBoolean() || !lifecycleDataWritesEnabled()) {
            return;
        }
        islands.existingContext(event.getPlayer()).ifPresent(context -> {
            context.factoryIsland().lastTickAt(System.currentTimeMillis());
            islands.save(context.factoryIsland());
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!active.getAsBoolean() || !machinesEnabled.getAsBoolean()) {
            return;
        }
        machines.markChunkStatus(event.getChunk(), MachineStatus.CHUNK_UNLOADED);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!active.getAsBoolean() || !machinesEnabled.getAsBoolean()) {
            return;
        }
        for (MachineInstance machine : machines.byChunk(event.getChunk())) {
            if (machine.status() == MachineStatus.CHUNK_UNLOADED) {
                machine.status(MachineStatus.SLEEPING);
                machines.saveLater(machine);
                itemNetworks.rebuildIsland(machine.islandUuid());
                power.rebuildIsland(machine.islandUuid());
            }
        }
    }

    private boolean lifecycleDataWritesEnabled() {
        return active.getAsBoolean();
    }
}
