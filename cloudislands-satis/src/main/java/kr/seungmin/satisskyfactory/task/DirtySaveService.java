package kr.seungmin.satisskyfactory.task;

import kr.lunaf.cloudislands.api.service.IslandAddonService;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DirtySaveService {
    private final JavaPlugin plugin;
    private final DatabaseService database;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualInventory> inventories = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceNode> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, FactoryIsland> islands = new ConcurrentHashMap<>();
    private final Object flushLock = new Object();
    private Consumer<DirtySaveBatch> coreStatePublisher;
    private Consumer<DirtyRowDelete> coreStateDeletePublisher;
    private BooleanSupplier machineWritesEnabled = () -> true;
    private Predicate<VirtualInventory> inventoryWritesEnabled = _inventory -> true;
    private BooleanSupplier nodeWritesEnabled = () -> true;
    private BooleanSupplier islandWritesEnabled = () -> true;
    private BukkitTask task;

    public DirtySaveService(JavaPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    public record DirtySaveBatch(Map<UUID, MachineInstance> machines, Map<UUID, VirtualInventory> inventories,
                                 Map<UUID, ResourceNode> nodes, Map<UUID, FactoryIsland> islands) {
    }

    public record DirtyRowDelete(UUID islandUuid, String key) {
    }

    public void coreStatePublisher(Consumer<DirtySaveBatch> coreStatePublisher) {
        this.coreStatePublisher = coreStatePublisher;
    }

    public void coreStateDeletePublisher(Consumer<DirtyRowDelete> coreStateDeletePublisher) {
        this.coreStateDeletePublisher = coreStateDeletePublisher;
    }

    public void writeGates(BooleanSupplier machineWritesEnabled, BooleanSupplier inventoryWritesEnabled,
                           BooleanSupplier nodeWritesEnabled, BooleanSupplier islandWritesEnabled) {
        this.machineWritesEnabled = machineWritesEnabled == null ? () -> true : machineWritesEnabled;
        BooleanSupplier inventoryGate = inventoryWritesEnabled == null ? () -> true : inventoryWritesEnabled;
        this.inventoryWritesEnabled = _inventory -> inventoryGate.getAsBoolean();
        this.nodeWritesEnabled = nodeWritesEnabled == null ? () -> true : nodeWritesEnabled;
        this.islandWritesEnabled = islandWritesEnabled == null ? () -> true : islandWritesEnabled;
    }

    public void inventoryWriteGate(Predicate<VirtualInventory> inventoryWritesEnabled) {
        this.inventoryWritesEnabled = inventoryWritesEnabled == null ? _inventory -> true : inventoryWritesEnabled;
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

    public boolean running() {
        return task != null;
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
        if (!machineWritesEnabled.getAsBoolean()) {
            return;
        }
        if (machine == null || machine.machineId() == null) {
            return;
        }
        machines.put(machine.machineId(), snapshot(machine));
    }

    public void forgetMachine(UUID machineId) {
        if (machineId == null) {
            return;
        }
        machines.remove(machineId);
    }

    public void deleteMachine(UUID islandUuid, UUID machineId) {
        if (machineId == null) {
            return;
        }
        forgetMachine(machineId);
        if (!machineWritesEnabled.getAsBoolean()) {
            return;
        }
        publishCoreDelete(islandUuid, IslandAddonService.tableStateKey("machines", machineId.toString()));
    }

    public void forgetMachines() {
        machines.clear();
    }

    public void markInventory(VirtualInventory inventory) {
        if (!inventoryWritesEnabled(inventory)) {
            return;
        }
        if (inventory == null || inventory.inventoryId() == null) {
            return;
        }
        inventories.put(inventory.inventoryId(), snapshot(inventory));
    }

    public void forgetInventory(UUID inventoryId) {
        if (inventoryId == null) {
            return;
        }
        inventories.remove(inventoryId);
    }

    public void deleteInventory(UUID islandUuid, UUID inventoryId) {
        if (inventoryId == null) {
            return;
        }
        forgetInventory(inventoryId);
        if (!inventoryWritesEnabled.getAsBoolean()) {
            return;
        }
        publishCoreDelete(islandUuid, IslandAddonService.tableStateKey("virtual_inventories", inventoryId.toString()));
    }

    public void forgetInventories() {
        inventories.clear();
    }

    public void forgetInventories(Predicate<VirtualInventory> filter) {
        if (filter == null) {
            return;
        }
        inventories.entrySet().removeIf(entry -> filter.test(entry.getValue()));
    }

    public void forgetIsland(UUID islandUuid) {
        if (islandUuid == null) {
            return;
        }
        islands.remove(islandUuid);
        machines.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
        inventories.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
        nodes.entrySet().removeIf(entry -> entry.getValue().islandUuid().equals(islandUuid));
    }

    public void markNode(ResourceNode node) {
        if (!nodeWritesEnabled.getAsBoolean()) {
            return;
        }
        if (node == null || node.nodeId() == null) {
            return;
        }
        nodes.put(node.nodeId(), snapshot(node));
    }

    public void forgetNodes() {
        nodes.clear();
    }

    public void markIsland(FactoryIsland island) {
        if (!islandWritesEnabled.getAsBoolean()) {
            return;
        }
        if (island == null || island.islandUuid() == null) {
            return;
        }
        islands.put(island.islandUuid(), snapshot(island));
    }

    public void forgetIslands() {
        islands.clear();
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
            Map<UUID, VirtualInventory> inventoryBatch = drainInventoriesIfEnabled(inventories);
            Map<UUID, MachineInstance> machineBatch = drainIfEnabled(machines, machineWritesEnabled);
            Map<UUID, ResourceNode> nodeBatch = drainIfEnabled(nodes, nodeWritesEnabled);
            Map<UUID, FactoryIsland> islandBatch = drainIfEnabled(islands, islandWritesEnabled);
            saveBatch("inventory", inventoryBatch, inventories, database::saveInventory);
            saveBatch("machine", machineBatch, machines, database::saveMachine);
            saveBatch("node", nodeBatch, nodes, database::saveNode);
            saveBatch("island", islandBatch, islands, database::saveIsland);
            publishCoreState(machineBatch, inventoryBatch, nodeBatch, islandBatch);
        }
    }

    private void flushIsland(UUID islandUuid) {
        if (islandUuid == null) {
            return;
        }
        synchronized (flushLock) {
            Map<UUID, VirtualInventory> inventoryBatch = drainIslandInventoriesIfEnabled(inventories, islandUuid);
            Map<UUID, MachineInstance> machineBatch = drainIslandIfEnabled(machines, islandUuid, machineWritesEnabled);
            Map<UUID, ResourceNode> nodeBatch = drainIslandIfEnabled(nodes, islandUuid, nodeWritesEnabled);
            FactoryIsland island = islandWritesEnabled.getAsBoolean() ? islands.remove(islandUuid) : null;
            if (!islandWritesEnabled.getAsBoolean()) {
                islands.remove(islandUuid);
            }
            Map<UUID, FactoryIsland> islandBatch = island == null ? Map.of() : Map.of(islandUuid, island);
            saveBatch("inventory", inventoryBatch, inventories, database::saveInventory);
            saveBatch("machine", machineBatch, machines, database::saveMachine);
            saveBatch("node", nodeBatch, nodes, database::saveNode);
            if (island != null) {
                saveBatch("island", islandBatch, islands, database::saveIsland);
            }
            publishCoreState(machineBatch, inventoryBatch, nodeBatch, islandBatch);
        }
    }

    private void publishCoreState(Map<UUID, MachineInstance> machineBatch, Map<UUID, VirtualInventory> inventoryBatch,
                                  Map<UUID, ResourceNode> nodeBatch, Map<UUID, FactoryIsland> islandBatch) {
        if (coreStatePublisher == null) {
            return;
        }
        if (machineBatch.isEmpty() && inventoryBatch.isEmpty() && nodeBatch.isEmpty() && islandBatch.isEmpty()) {
            return;
        }
        try {
            coreStatePublisher.accept(new DirtySaveBatch(
                    Map.copyOf(machineBatch),
                    Map.copyOf(inventoryBatch),
                    Map.copyOf(nodeBatch),
                    Map.copyOf(islandBatch)
            ));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Core API Satis state publish failed: " + exception.getMessage());
        }
    }

    private void publishCoreDelete(UUID islandUuid, String key) {
        if (coreStateDeletePublisher == null || islandUuid == null || key == null || key.isBlank()) {
            return;
        }
        try {
            coreStateDeletePublisher.accept(new DirtyRowDelete(islandUuid, key));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Core API Satis state delete failed: " + exception.getMessage());
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

    private <T> Map<UUID, T> drainIfEnabled(Map<UUID, T> source, BooleanSupplier enabled) {
        if (!enabled.getAsBoolean()) {
            source.clear();
            return Map.of();
        }
        return drain(source);
    }

    private <T> Map<UUID, T> drainIsland(Map<UUID, T> source, UUID islandUuid) {
        Map<UUID, T> snapshot = source.entrySet().stream()
                .filter(entry -> islandUuid.equals(islandUuid(entry.getValue())))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        snapshot.forEach((id, value) -> source.remove(id, value));
        return snapshot;
    }

    private <T> Map<UUID, T> drainIslandIfEnabled(Map<UUID, T> source, UUID islandUuid, BooleanSupplier enabled) {
        if (!enabled.getAsBoolean()) {
            drainIsland(source, islandUuid);
            return Map.of();
        }
        return drainIsland(source, islandUuid);
    }

    private Map<UUID, VirtualInventory> drainInventoriesIfEnabled(Map<UUID, VirtualInventory> source) {
        Map<UUID, VirtualInventory> snapshot = source.entrySet().stream()
                .filter(entry -> inventoryWritesEnabled(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        source.entrySet().removeIf(entry -> !inventoryWritesEnabled(entry.getValue()) || snapshot.containsKey(entry.getKey()));
        return snapshot;
    }

    private Map<UUID, VirtualInventory> drainIslandInventoriesIfEnabled(Map<UUID, VirtualInventory> source, UUID islandUuid) {
        Map<UUID, VirtualInventory> snapshot = source.entrySet().stream()
                .filter(entry -> islandUuid.equals(entry.getValue().islandUuid()))
                .filter(entry -> inventoryWritesEnabled(entry.getValue()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        source.entrySet().removeIf(entry -> islandUuid.equals(entry.getValue().islandUuid()) && (!inventoryWritesEnabled(entry.getValue()) || snapshot.containsKey(entry.getKey())));
        return snapshot;
    }

    private boolean inventoryWritesEnabled(VirtualInventory inventory) {
        try {
            return inventoryWritesEnabled.test(inventory);
        } catch (RuntimeException ignored) {
            return false;
        }
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
        copy.activeWorld(island.activeWorld());
        copy.activeCenterX(island.activeCenterX());
        copy.activeCenterY(island.activeCenterY());
        copy.activeCenterZ(island.activeCenterZ());
        return copy;
    }
}
