package kr.seungmin.satisskyfactory.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VirtualInventory {
    private final UUID inventoryId;
    private final UUID islandUuid;
    private final String holderType;
    private final String holderId;
    private final long capacity;
    private final Map<String, Long> items = new HashMap<>();

    public VirtualInventory(UUID inventoryId, UUID islandUuid, String holderType, String holderId, long capacity) {
        this.inventoryId = inventoryId;
        this.islandUuid = islandUuid;
        this.holderType = holderType;
        this.holderId = holderId;
        this.capacity = Math.max(0L, capacity);
    }

    public UUID inventoryId() { return inventoryId; }
    public UUID islandUuid() { return islandUuid; }
    public String holderType() { return holderType; }
    public String holderId() { return holderId; }
    public long capacity() { return capacity; }
    public synchronized Map<String, Long> items() { return Map.copyOf(items); }

    public synchronized long used() {
        long total = 0L;
        for (long amount : items.values()) {
            try {
                total = Math.addExact(total, Math.max(0L, amount));
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    public synchronized long remainingCapacity() {
        long used = used();
        if (capacity <= used) {
            return 0;
        }
        return capacity - used;
    }

    public synchronized long amount(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0L;
        }
        return items.getOrDefault(itemId, 0L);
    }

    public synchronized boolean canAdd(String itemId, long amount) {
        return itemId != null && !itemId.isBlank() && amount >= 0 && amount <= remainingCapacity();
    }

    public synchronized boolean add(String itemId, long amount) {
        if (amount <= 0 || !canAdd(itemId, amount)) {
            return false;
        }
        long current = amount(itemId);
        try {
            items.put(itemId, Math.addExact(current, amount));
        } catch (ArithmeticException overflow) {
            return false;
        }
        return true;
    }

    public synchronized boolean remove(String itemId, long amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0 || amount(itemId) < amount) {
            return false;
        }
        long next = amount(itemId) - amount;
        if (next == 0) {
            items.remove(itemId);
        } else {
            items.put(itemId, next);
        }
        return true;
    }

    public synchronized void set(String itemId, long amount) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (amount <= 0) {
            items.remove(itemId);
        } else {
            items.put(itemId, amount);
        }
    }
}
