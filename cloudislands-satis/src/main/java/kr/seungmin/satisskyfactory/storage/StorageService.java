package kr.seungmin.satisskyfactory.storage;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.task.DirtySaveService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class StorageService {
    private final DatabaseService database;
    private final long defaultCapacity;
    private final Map<UUID, VirtualInventory> cache = new ConcurrentHashMap<>();
    private DirtySaveService dirtySaves;
    private BooleanSupplier islandWritesEnabled = () -> true;
    private BooleanSupplier machineInventoryWritesEnabled = () -> true;

    public StorageService(DatabaseService database, long defaultCapacity) {
        this.database = database;
        this.defaultCapacity = defaultCapacity;
    }

    public VirtualInventory islandStorage(UUID islandUuid) {
        Optional<VirtualInventory> cached = cache.values().stream()
                .filter(inventory -> inventory.islandUuid().equals(islandUuid))
                .filter(inventory -> inventory.holderType().equals("ISLAND"))
                .filter(inventory -> inventory.holderId().equals(islandUuid.toString()))
                .findFirst();
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<VirtualInventory> existing = database.findInventoryByHolder(islandUuid, "ISLAND", islandUuid.toString());
        if (existing.isPresent()) {
            cache.put(existing.get().inventoryId(), existing.get());
            return existing.get();
        }
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, "ISLAND", islandUuid.toString(), defaultCapacity);
        if (!saveNow(inventory)) {
            throw new IllegalStateException("Island storage writes are not available");
        }
        return inventory;
    }

    public Optional<VirtualInventory> islandStorageIfAllowed(UUID islandUuid) {
        Optional<VirtualInventory> existing = findIslandStorage(islandUuid);
        if (existing.isPresent()) {
            return existing;
        }
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, "ISLAND", islandUuid.toString(), defaultCapacity);
        return saveNow(inventory) ? Optional.of(inventory) : Optional.empty();
    }

    public Optional<VirtualInventory> findIslandStorage(UUID islandUuid) {
        Optional<VirtualInventory> cached = cache.values().stream()
                .filter(inventory -> inventory.islandUuid().equals(islandUuid))
                .filter(inventory -> inventory.holderType().equals("ISLAND"))
                .filter(inventory -> inventory.holderId().equals(islandUuid.toString()))
                .findFirst();
        if (cached.isPresent()) {
            return cached;
        }
        Optional<VirtualInventory> existing = database.findInventoryByHolder(islandUuid, "ISLAND", islandUuid.toString());
        existing.ifPresent(inventory -> cache.put(inventory.inventoryId(), inventory));
        return existing;
    }

    public VirtualInventory createMachineInventory(UUID islandUuid, UUID machineId, String holderType, long capacity) {
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, holderType, machineId.toString(), capacity);
        if (!saveNow(inventory)) {
            throw new IllegalStateException("Machine inventory writes are not available");
        }
        return inventory;
    }

    public Optional<VirtualInventory> createMachineInventoryIfAllowed(UUID islandUuid, UUID machineId, String holderType, long capacity) {
        VirtualInventory inventory = new VirtualInventory(UUID.randomUUID(), islandUuid, holderType, machineId.toString(), capacity);
        return saveIfAllowed(inventory) ? Optional.of(inventory) : Optional.empty();
    }

    public Optional<VirtualInventory> get(UUID inventoryId) {
        if (inventoryId == null) {
            return Optional.empty();
        }
        VirtualInventory cached = cache.get(inventoryId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<VirtualInventory> loaded = database.loadInventory(inventoryId);
        loaded.ifPresent(inventory -> cache.put(inventory.inventoryId(), inventory));
        return loaded;
    }

    public boolean save(VirtualInventory inventory) {
        return saveIfAllowed(inventory);
    }

    public boolean saveIfAllowed(VirtualInventory inventory) {
        if (!writesEnabled(inventory)) {
            return false;
        }
        cache.put(inventory.inventoryId(), inventory);
        if (dirtySaves != null) {
            dirtySaves.markInventory(inventory);
            return true;
        }
        database.saveInventory(inventory);
        return true;
    }

    public boolean saveNow(VirtualInventory inventory) {
        if (!writesEnabled(inventory)) {
            return false;
        }
        cache.put(inventory.inventoryId(), inventory);
        database.saveInventory(inventory);
        return true;
    }

    public boolean canDelete(UUID inventoryId) {
        if (inventoryId == null) {
            return false;
        }
        VirtualInventory existing = cache.get(inventoryId);
        if (existing == null) {
            existing = database.loadInventory(inventoryId).orElse(null);
            if (existing != null) {
                cache.put(existing.inventoryId(), existing);
            }
        }
        return deleteWritesEnabled(existing);
    }

    public boolean delete(UUID inventoryId) {
        if (inventoryId == null) {
            return false;
        }
        if (!canDelete(inventoryId)) {
            return false;
        }
        VirtualInventory removed = cache.remove(inventoryId);
        if (dirtySaves != null) {
            if (removed == null) {
                dirtySaves.forgetInventory(inventoryId);
            } else {
                dirtySaves.deleteInventory(removed.islandUuid(), inventoryId);
            }
        }
        database.deleteInventory(inventoryId);
        return true;
    }

    public void forgetIsland(UUID islandUuid) {
        cache.entrySet().removeIf(entry -> {
            boolean sameIsland = entry.getValue().islandUuid().equals(islandUuid);
            if (sameIsland && dirtySaves != null) {
                dirtySaves.forgetInventory(entry.getKey());
            }
            return sameIsland;
        });
    }

    public void clear() {
        cache.clear();
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.islandWritesEnabled = writesEnabled == null ? () -> true : writesEnabled;
        this.machineInventoryWritesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    public void writeGates(BooleanSupplier islandWritesEnabled, BooleanSupplier machineInventoryWritesEnabled) {
        this.islandWritesEnabled = islandWritesEnabled == null ? () -> true : islandWritesEnabled;
        this.machineInventoryWritesEnabled = machineInventoryWritesEnabled == null ? () -> true : machineInventoryWritesEnabled;
    }

    private boolean writesEnabled(VirtualInventory inventory) {
        BooleanSupplier gate = isMachineInventory(inventory) ? machineInventoryWritesEnabled : islandWritesEnabled;
        try {
            return gate.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean deleteWritesEnabled(VirtualInventory inventory) {
        if (inventory != null) {
            return writesEnabled(inventory);
        }
        try {
            return islandWritesEnabled.getAsBoolean() || machineInventoryWritesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isMachineInventory(VirtualInventory inventory) {
        if (inventory == null || inventory.holderType() == null) {
            return false;
        }
        return inventory.holderType().startsWith("MACHINE_");
    }
}
