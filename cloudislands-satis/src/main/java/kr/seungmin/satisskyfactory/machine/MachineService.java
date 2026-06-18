package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import kr.seungmin.satisskyfactory.util.LocationKey;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MachineService {
    private final DatabaseService database;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final Map<UUID, MachineInstance> machines = new ConcurrentHashMap<>();
    private final Map<LocationKey, UUID> byLocation = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private DirtySaveService dirtySaves;
    private BooleanSupplier writesEnabled = () -> true;
    private boolean loaded;

    public MachineService(DatabaseService database, MachineDefinitionService definitions, StorageService storage) {
        this.database = database;
        this.definitions = definitions;
        this.storage = storage;
        load();
    }

    public void load() {
        machines.clear();
        byLocation.clear();
        for (MachineInstance machine : database.loadMachines()) {
            machines.put(machine.machineId(), machine);
            byLocation.put(LocationKey.from(machine.location()), machine.machineId());
        }
        loaded = true;
        revision.incrementAndGet();
    }

    public void clear() {
        machines.clear();
        byLocation.clear();
        loaded = false;
        revision.incrementAndGet();
    }

    public Optional<MachineInstance> at(Location location) {
        UUID id = byLocation.get(LocationKey.from(location));
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id));
    }

    public Optional<MachineInstance> find(UUID machineId) {
        return Optional.ofNullable(machines.get(machineId));
    }

    public Optional<MachineInstance> create(UUID islandUuid, UUID ownerUuid, String typeId, Location location, BlockFace direction) {
        if (!loaded) {
            throw new IllegalStateException("Machine service is not active");
        }
        if (!writesEnabled()) {
            return Optional.empty();
        }
        MachineDefinition definition = definitions.get(typeId).orElseThrow();
        MachineInstance machine = new MachineInstance(UUID.randomUUID(), islandUuid, ownerUuid, typeId, definition.tier(), BlockKey.from(location));
        machine.direction(direction);
        Optional<VirtualInventory> inputInventory = storage.createMachineInventoryIfAllowed(islandUuid, machine.machineId(), "MACHINE_INPUT", definition.inputCapacity());
        if (inputInventory.isEmpty()) {
            return Optional.empty();
        }
        Optional<VirtualInventory> outputInventory = storage.createMachineInventoryIfAllowed(islandUuid, machine.machineId(), "MACHINE_OUTPUT", definition.outputCapacity());
        if (outputInventory.isEmpty()) {
            storage.delete(inputInventory.get().inventoryId());
            return Optional.empty();
        }
        VirtualInventory input = inputInventory.get();
        VirtualInventory output = outputInventory.get();
        machine.inputInventoryId(input.inventoryId());
        machine.outputInventoryId(output.inventoryId());
        if (!save(machine)) {
            storage.delete(input.inventoryId());
            storage.delete(output.inventoryId());
            return Optional.empty();
        }
        revision.incrementAndGet();
        return Optional.of(machine);
    }

    public boolean save(MachineInstance machine) {
        if (!loaded) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        machines.put(machine.machineId(), machine);
        byLocation.put(LocationKey.from(machine.location()), machine.machineId());
        database.saveMachine(machine);
        return true;
    }

    public boolean saveLater(MachineInstance machine) {
        if (!loaded) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        machines.put(machine.machineId(), machine);
        byLocation.put(LocationKey.from(machine.location()), machine.machineId());
        if (dirtySaves == null) {
            database.saveMachine(machine);
        } else {
            dirtySaves.markMachine(machine);
        }
        return true;
    }

    public void reactivate(MachineInstance machine) {
        if (machine.status() == MachineStatus.BROKEN || machine.status() == MachineStatus.INVALID_LOCATION
                || machine.status() == MachineStatus.CHUNK_UNLOADED || machine.status() == MachineStatus.MAINTENANCE_LOCKED) {
            return;
        }
        MachineStatus previousStatus = machine.status();
        if (machine.status() != MachineStatus.ACTIVE) {
            machine.status(MachineStatus.SLEEPING);
        }
        if (!saveLater(machine)) {
            machine.status(previousStatus);
            return;
        }
        revision.incrementAndGet();
    }

    public void reactivatePowerBlocked(UUID islandUuid) {
        boolean changed = false;
        for (MachineInstance machine : byIsland(islandUuid)) {
            if (machine.status() != MachineStatus.NO_POWER) {
                continue;
            }
            MachineStatus previousStatus = machine.status();
            machine.status(MachineStatus.SLEEPING);
            if (saveLater(machine)) {
                changed = true;
            } else {
                machine.status(previousStatus);
            }
        }
        if (changed) {
            revision.incrementAndGet();
        }
    }

    public boolean remove(MachineInstance machine) {
        if (!loaded) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        if (hasBufferedItems(machine)) {
            return false;
        }
        return delete(machine);
    }

    public boolean forceRemove(MachineInstance machine) {
        if (!loaded) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        if (!flushInventories(machine)) {
            if (!clearInventories(machine)) {
                return false;
            }
        }
        return delete(machine);
    }

    private boolean delete(MachineInstance machine) {
        if (!deleteInventories(machine)) {
            return false;
        }
        machine.status(MachineStatus.SLEEPING);
        machines.remove(machine.machineId());
        byLocation.remove(LocationKey.from(machine.location()));
        boolean canWrite = writesEnabled();
        if (dirtySaves != null) {
            if (canWrite) {
                dirtySaves.deleteMachine(machine.islandUuid(), machine.machineId());
            } else {
                dirtySaves.forgetMachine(machine.machineId());
            }
        }
        if (canWrite) {
            database.deleteMachine(machine.machineId());
        }
        revision.incrementAndGet();
        return true;
    }

    private boolean deleteInventories(MachineInstance machine) {
        List<VirtualInventory> inventories = machineInventories(machine);
        Set<UUID> inventoryIds = new HashSet<>();
        if (machine.inputInventoryId() != null) {
            inventoryIds.add(machine.inputInventoryId());
        }
        if (machine.outputInventoryId() != null) {
            inventoryIds.add(machine.outputInventoryId());
        }
        for (UUID inventoryId : inventoryIds) {
            if (!storage.canDelete(inventoryId)) {
                return false;
            }
        }
        List<VirtualInventory> deletedInventories = new ArrayList<>();
        for (UUID inventoryId : inventoryIds) {
            if (!storage.delete(inventoryId)) {
                restoreDeletedInventories(deletedInventories);
                return false;
            }
            inventories.stream()
                    .filter(inventory -> inventory.inventoryId().equals(inventoryId))
                    .findFirst()
                    .ifPresent(deletedInventories::add);
        }
        return true;
    }

    private void restoreDeletedInventories(List<VirtualInventory> inventories) {
        for (VirtualInventory inventory : inventories) {
            if (!storage.saveIfAllowed(inventory)) {
                storage.delete(inventory.inventoryId());
            }
        }
    }

    private boolean hasBufferedItems(MachineInstance machine) {
        return machineInventories(machine).stream().anyMatch(inventory -> inventory.used() > 0);
    }

    private boolean flushInventories(MachineInstance machine) {
        VirtualInventory islandStorage = storage.islandStorage(machine.islandUuid());
        List<VirtualInventory> buffers = machineInventories(machine);
        long bufferedItems = buffers.stream().mapToLong(VirtualInventory::used).sum();
        if (!islandStorage.canAdd("__machine_buffer__", bufferedItems)) {
            return false;
        }
        Map<String, Long> islandBefore = islandStorage.items();
        Map<UUID, Map<String, Long>> bufferBefore = new java.util.HashMap<>();
        for (VirtualInventory buffer : buffers) {
            bufferBefore.put(buffer.inventoryId(), buffer.items());
            for (Map.Entry<String, Long> entry : buffer.items().entrySet()) {
                islandStorage.add(entry.getKey(), entry.getValue());
            }
        }
        Map<String, Long> islandAfter = islandStorage.items();
        if (!storage.saveIfAllowed(islandStorage)) {
            restoreInventory(islandStorage, islandBefore);
            return false;
        }
        List<VirtualInventory> clearedBuffers = new ArrayList<>();
        for (VirtualInventory buffer : buffers) {
            new ArrayList<>(buffer.items().keySet()).forEach(itemId -> buffer.set(itemId, 0));
            if (!storage.saveIfAllowed(buffer)) {
                restoreInventory(buffer, bufferBefore.getOrDefault(buffer.inventoryId(), Map.of()));
                restoreFlushedInventories(islandStorage, islandBefore, islandAfter, clearedBuffers, bufferBefore);
                return false;
            }
            clearedBuffers.add(buffer);
        }
        return true;
    }

    private void restoreFlushedInventories(VirtualInventory islandStorage, Map<String, Long> islandBefore,
                                           Map<String, Long> islandAfter, List<VirtualInventory> clearedBuffers,
                                           Map<UUID, Map<String, Long>> bufferBefore) {
        restoreInventory(islandStorage, islandBefore);
        if (!storage.saveIfAllowed(islandStorage)) {
            restoreInventory(islandStorage, islandAfter);
        }
        restoreClearedInventories(clearedBuffers, bufferBefore);
    }

    private void restoreClearedInventories(List<VirtualInventory> clearedBuffers, Map<UUID, Map<String, Long>> bufferBefore) {
        for (VirtualInventory buffer : clearedBuffers) {
            restoreInventory(buffer, bufferBefore.getOrDefault(buffer.inventoryId(), Map.of()));
            if (!storage.saveIfAllowed(buffer)) {
                restoreInventory(buffer, Map.of());
            }
        }
    }

    private void restoreInventory(VirtualInventory inventory, Map<String, Long> snapshot) {
        new ArrayList<>(inventory.items().keySet()).forEach(itemId -> inventory.set(itemId, 0));
        snapshot.forEach(inventory::set);
    }

    private boolean clearInventories(MachineInstance machine) {
        Map<UUID, Map<String, Long>> bufferBefore = new java.util.HashMap<>();
        List<VirtualInventory> clearedBuffers = new ArrayList<>();
        for (VirtualInventory buffer : machineInventories(machine)) {
            bufferBefore.put(buffer.inventoryId(), buffer.items());
            new ArrayList<>(buffer.items().keySet()).forEach(itemId -> buffer.set(itemId, 0));
            if (!storage.saveIfAllowed(buffer)) {
                restoreInventory(buffer, bufferBefore.getOrDefault(buffer.inventoryId(), Map.of()));
                restoreClearedInventories(clearedBuffers, bufferBefore);
                return false;
            }
            clearedBuffers.add(buffer);
        }
        return true;
    }

    private List<VirtualInventory> machineInventories(MachineInstance machine) {
        Set<UUID> inventoryIds = new HashSet<>();
        if (machine.inputInventoryId() != null) {
            inventoryIds.add(machine.inputInventoryId());
        }
        if (machine.outputInventoryId() != null) {
            inventoryIds.add(machine.outputInventoryId());
        }
        return inventoryIds.stream()
                .map(storage::get)
                .flatMap(Optional::stream)
                .toList();
    }

    public Collection<MachineInstance> all() {
        return new ArrayList<>(machines.values());
    }

    public long revision() {
        return revision.get();
    }

    public Collection<MachineInstance> byIsland(UUID islandUuid) {
        return machines.values().stream().filter(machine -> machine.islandUuid().equals(islandUuid)).toList();
    }

    public boolean remapIslandRegion(UUID islandUuid, String worldName, int deltaX, int deltaY, int deltaZ) {
        if (!loaded) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        boolean changed = false;
        for (MachineInstance machine : byIsland(islandUuid)) {
            if (worldName.equals(machine.location().world()) && deltaX == 0 && deltaY == 0 && deltaZ == 0) {
                continue;
            }
            LocationKey previousLocation = LocationKey.from(machine.location());
            MachineInstance remapped = copyWithLocation(machine, new BlockKey(
                    worldName,
                    machine.location().x() + deltaX,
                    machine.location().y() + deltaY,
                    machine.location().z() + deltaZ
            ));
            if (!saveLater(remapped)) {
                continue;
            }
            byLocation.remove(previousLocation);
            changed = true;
        }
        if (changed) {
            revision.incrementAndGet();
        }
        return changed;
    }

    private MachineInstance copyWithLocation(MachineInstance machine, BlockKey location) {
        MachineInstance copy = new MachineInstance(
                machine.machineId(),
                machine.islandUuid(),
                machine.ownerUuid(),
                machine.typeId(),
                machine.tier(),
                location
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

    public void forgetIsland(UUID islandUuid) {
        for (MachineInstance machine : byIsland(islandUuid)) {
            machines.remove(machine.machineId());
            byLocation.remove(LocationKey.from(machine.location()));
            if (dirtySaves != null) {
                dirtySaves.forgetMachine(machine.machineId());
            }
        }
        revision.incrementAndGet();
    }

    public long factoryScore(UUID islandUuid) {
        return factoryScore(islandUuid, 1);
    }

    public long factoryScore(UUID islandUuid, int islandTier) {
        long baseScore = 0;
        long logisticsScore = 0;
        long storageScore = 0;
        long powerScore = 0;
        for (MachineInstance machine : byIsland(islandUuid)) {
            MachineDefinition definition = definitions.get(machine.typeId()).orElse(null);
            if (definition == null) {
                continue;
            }
            baseScore += definition.factoryScore();
            if (definition.isLogistics()) {
                logisticsScore += Math.max(1, definition.logisticsThroughput() / 8L);
            }
            if (definition.isStorage()) {
                storageScore += Math.max(1, definition.inputCapacity() / 500L);
            }
            if (definition.isGenerator()) {
                powerScore += Math.max(1, Math.round(definition.powerGeneration() / 4.0));
            }
            if (definition.isBattery()) {
                powerScore += Math.max(1, Math.round(definition.batteryCapacity() / 500.0));
            }
        }
        long islandTierBonus = Math.max(0, islandTier - 1L) * 25L;
        return baseScore + logisticsScore + storageScore + powerScore + islandTierBonus;
    }

    public long maintenanceScore(UUID islandUuid) {
        return byIsland(islandUuid).stream()
                .map(machine -> definitions.get(machine.typeId()).orElse(null))
                .filter(definition -> definition != null)
                .mapToLong(MachineDefinition::maintenanceScore)
                .sum();
    }

    public Collection<MachineInstance> byChunk(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        return machines.values().stream()
                .filter(machine -> machine.location().world().equals(worldName))
                .filter(machine -> machine.location().chunkX() == chunkX && machine.location().chunkZ() == chunkZ)
                .toList();
    }

    public void markChunkStatus(Chunk chunk, MachineStatus status) {
        if (!loaded) {
            return;
        }
        for (MachineInstance machine : byChunk(chunk)) {
            if (machine.status() == MachineStatus.BROKEN || machine.status() == status) {
                continue;
            }
            MachineStatus previousStatus = machine.status();
            machine.status(status);
            if (!saveLater(machine)) {
                machine.status(previousStatus);
            }
        }
    }

    public Collection<MachineInstance> connectedTo(MachineInstance start) {
        return connectedTo(start, machine -> true);
    }

    public Collection<MachineInstance> connectedTo(MachineInstance start, Predicate<MachineInstance> traversable) {
        Set<UUID> visitedMachines = new HashSet<>();
        Set<LocationKey> visitedLocations = new HashSet<>();
        Queue<LocationKey> queue = new ArrayDeque<>();
        LocationKey startLocation = LocationKey.from(start.location());
        queue.add(startLocation);
        visitedLocations.add(startLocation);

        while (!queue.isEmpty()) {
            LocationKey location = queue.poll();
            UUID machineId = byLocation.get(location);
            if (machineId == null || !visitedMachines.add(machineId)) {
                continue;
            }
            MachineInstance machine = machines.get(machineId);
            if (machine == null || !machine.islandUuid().equals(start.islandUuid())) {
                continue;
            }
            if (!machine.machineId().equals(start.machineId()) && !traversable(machine, traversable)) {
                continue;
            }
            for (LocationKey neighbor : neighbors(location)) {
                if (visitedLocations.add(neighbor) && byLocation.containsKey(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visitedMachines.stream()
                .map(machines::get)
                .filter(machine -> machine != null && machine.islandUuid().equals(start.islandUuid()))
                .toList();
    }

    private List<LocationKey> neighbors(LocationKey location) {
        return List.of(
                location.relative(1, 0, 0),
                location.relative(-1, 0, 0),
                location.relative(0, 1, 0),
                location.relative(0, -1, 0),
                location.relative(0, 0, 1),
                location.relative(0, 0, -1)
        );
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    private boolean writesEnabled() {
        try {
            return writesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean traversable(MachineInstance machine, Predicate<MachineInstance> traversable) {
        try {
            return traversable.test(machine);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
