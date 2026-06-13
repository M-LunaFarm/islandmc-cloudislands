package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DirtySaveService {
    private final JavaPlugin plugin;
    private final DatabaseService database;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualInventory> inventories = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceNode> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, FactoryIsland> islands = new ConcurrentHashMap<>();
    private final Object flushLock = new Object();
    private BukkitTask task;

    public DirtySaveService(JavaPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void start(long intervalTicks) {
        stop();
        task = SchedulerUtil.asyncRepeating(plugin, this::flushSafely, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flushSafely();
    }

    public void discard() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        synchronized (flushLock) {
            machines.clear();
            inventories.clear();
            nodes.clear();
            islands.clear();
        }
    }

    public void markMachine(MachineInstance machine) {
        machines.put(machine.machineId(), snapshot(machine));
    }

    public void forgetMachine(UUID machineId) {
        machines.remove(machineId);
    }

    public void forgetMachines() {
        machines.clear();
    }

    public void markInventory(VirtualInventory inventory) {
        inventories.put(inventory.inventoryId(), snapshot(inventory));
    }

    public void forgetInventory(UUID inventoryId) {
        inventories.remove(inventoryId);
    }

    public void forgetIsland(UUID islandUuid) {
        islands.remove(islandUuid);
        machines.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
        inventories.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
        nodes.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
    }

    public void markNode(ResourceNode node) {
        nodes.put(node.nodeId(), snapshot(node));
    }

    public void forgetNodes() {
        nodes.clear();
    }

    public void markIsland(FactoryIsland island) {
        islands.put(island.islandUuid(), snapshot(island));
    }

    public void flushSafely() {
        try {
            flush();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Dirty save flush failed: " + exception.getMessage());
        }
    }

    public void flushIslandSafely(UUID islandUuid) {
        try {
            flushIsland(islandUuid);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Dirty save flush failed for island " + islandUuid + ": " + exception.getMessage());
        }
    }

    private void flush() {
        synchronized (flushLock) {
            saveBatch("inventory", drain(inventories), inventories, database::saveInventory);
            saveBatch("machine", drain(machines), machines, database::saveMachine);
            saveBatch("node", drain(nodes), nodes, database::saveNode);
            saveBatch("island", drain(islands), islands, database::saveIsland);
        }
    }

    private void flushIsland(UUID islandUuid) {
        if (islandUuid == null) {
            return;
        }
        synchronized (flushLock) {
            saveBatch("inventory", drainIsland(inventories, islandUuid), inventories, database::saveInventory);
            saveBatch("machine", drainIsland(machines, islandUuid), machines, database::saveMachine);
            saveBatch("node", drainIsland(nodes, islandUuid), nodes, database::saveNode);
            FactoryIsland island = islands.remove(islandUuid);
            if (island != null) {
                saveBatch("island", Map.of(islandUuid, island), islands, database::saveIsland);
            }
        }
    }

    private <T> void saveBatch(String label, Map<UUID, T> snapshot, Map<UUID, T> retryQueue, Consumer<T> saver) {
        snapshot.forEach((id, value) -> {
            try {
                saver.accept(value);
            } catch (RuntimeException exception) {
                retryQueue.put(id, value);
                plugin.getLogger().warning("Dirty save failed for " + label + " " + id + ": " + exception.getMessage());
            }
        });
    }

    private <T> Map<UUID, T> drain(Map<UUID, T> source) {
        Map<UUID, T> snapshot = Map.copyOf(source);
        snapshot.forEach((id, value) -> source.remove(id, value));
        return snapshot;
    }

    private <T> Map<UUID, T> drainIsland(Map<UUID, T> source, UUID islandUuid) {
        Map<UUID, T> snapshot = source.entrySet().stream()
                .filter(entry -> islandUuid.equals(islandUuid(entry.getValue())))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        snapshot.forEach((id, value) -> source.remove(id, value));
        return snapshot;
    }

    private UUID islandUuid(Object value) {
        if (value instanceof MachineInstance machine) {
            return machine.islandUuid();
        }
        if (value instanceof VirtualInventory inventory) {
            return inventory.islandUuid();
        }
        if (value instanceof ResourceNode node) {
            return node.islandUuid();
        }
        if (value instanceof FactoryIsland island) {
            return island.islandUuid();
        }
        return null;
    }

    private MachineInstance snapshot(MachineInstance machine) {
        MachineInstance copy = new MachineInstance(
                machine.machineId(),
                machine.islandUuid(),
                machine.ownerUuid(),
                machine.typeId(),
                machine.tier(),
                machine.location()
        );
        copy.direction(machine.direction());
        copy.status(machine.status());
        copy.inputInventoryId(machine.inputInventoryId());
        copy.outputInventoryId(machine.outputInventoryId());
        copy.powerNetworkId(machine.powerNetworkId());
        copy.itemNetworkId(machine.itemNetworkId());
        copy.linkedResourceNodeId(machine.linkedResourceNodeId());
        copy.configJson(machine.configJson());
        copy.selectedRecipeId(machine.selectedRecipeId());
        copy.lastProcessAt(machine.lastProcessAt());
        copy.wear(machine.wear());
        copy.createdAt(machine.createdAt());
        copy.updatedAt(machine.updatedAt());
        return copy;
    }

    private VirtualInventory snapshot(VirtualInventory inventory) {
        VirtualInventory copy = new VirtualInventory(
                inventory.inventoryId(),
                inventory.islandUuid(),
                inventory.holderType(),
                inventory.holderId(),
                inventory.capacity()
        );
        inventory.items().forEach(copy::set);
        return copy;
    }

    private ResourceNode snapshot(ResourceNode node) {
        return new ResourceNode(
                node.nodeId(),
                node.islandUuid(),
                node.nodeType(),
                node.resourceId(),
                node.purity(),
                node.remaining(),
                node.maxRemaining(),
                node.regenPerHour(),
                node.requiredMachineTier(),
                node.location(),
                node.createdAt(),
                node.updatedAt()
        );
    }

    private FactoryIsland snapshot(FactoryIsland island) {
        FactoryIsland copy = new FactoryIsland(island.islandUuid(), island.ownerUuid());
        copy.tier(island.tier());
        copy.researchPoints(island.researchPoints());
        copy.reputation(island.reputation());
        copy.maintenanceDebt(island.maintenanceDebt());
        copy.maintenanceStatus(island.maintenanceStatus());
        copy.factoryScore(island.factoryScore());
        copy.lastMaintenanceAt(island.lastMaintenanceAt());
        copy.lastTickAt(island.lastTickAt());
        copy.createdAt(island.createdAt());
        copy.updatedAt(island.updatedAt());
        copy.emergencyContractsUsedToday(island.emergencyContractsUsedToday());
        return copy;
    }
}
