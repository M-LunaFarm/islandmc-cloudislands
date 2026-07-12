package kr.seungmin.satisskyfactory.storage;

import java.math.BigInteger;
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

    public synchronized boolean exchange(Map<String, Long> removals, Map<String, Long> additions) {
        Map<String, Long> next = new HashMap<>(items);
        if (!applyRemovals(next, removals) || !applyAdditions(next, additions)) {
            return false;
        }
        BigInteger total = BigInteger.ZERO;
        for (long amount : next.values()) {
            total = total.add(BigInteger.valueOf(amount));
        }
        if (total.compareTo(BigInteger.valueOf(capacity)) > 0) {
            return false;
        }
        items.clear();
        items.putAll(next);
        return true;
    }

    private static boolean applyRemovals(Map<String, Long> target, Map<String, Long> removals) {
        for (Map.Entry<String, Long> entry : safe(removals).entrySet()) {
            String itemId = entry.getKey();
            Long amount = entry.getValue();
            if (!valid(itemId, amount)) {
                return false;
            }
            long current = target.getOrDefault(itemId, 0L);
            if (current < amount) {
                return false;
            }
            long remaining = current - amount;
            if (remaining == 0L) {
                target.remove(itemId);
            } else {
                target.put(itemId, remaining);
            }
        }
        return true;
    }

    private static boolean applyAdditions(Map<String, Long> target, Map<String, Long> additions) {
        for (Map.Entry<String, Long> entry : safe(additions).entrySet()) {
            String itemId = entry.getKey();
            Long amount = entry.getValue();
            if (!valid(itemId, amount)) {
                return false;
            }
            try {
                target.put(itemId, Math.addExact(target.getOrDefault(itemId, 0L), amount));
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Long> safe(Map<String, Long> values) {
        return values == null ? Map.of() : values;
    }

    private static boolean valid(String itemId, Long amount) {
        return itemId != null && !itemId.isBlank() && amount != null && amount > 0L;
    }
}
