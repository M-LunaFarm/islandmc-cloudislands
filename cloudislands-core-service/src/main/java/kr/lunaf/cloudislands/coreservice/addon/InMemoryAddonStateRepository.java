package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAddonStateRepository implements AddonStateRepository {
    private final Map<String, Map<String, String>> states = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> islandStates = new ConcurrentHashMap<>();

    @Override
    public Map<String, Integer> globalStateCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        states.forEach((addonId, state) -> {
            synchronized (state) {
                counts.put(addonId, state.size());
            }
        });
        return Map.copyOf(counts);
    }

    @Override
    public Map<String, Integer> islandStateCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        islandStates.forEach((stateId, state) -> {
            int separator = stateId.lastIndexOf('/');
            if (separator <= 0) {
                return;
            }
            String addonId = stateId.substring(0, separator);
            synchronized (state) {
                counts.merge(addonId, state.size(), Integer::sum);
            }
        });
        return Map.copyOf(counts);
    }

    @Override
    public Map<String, String> list(String addonId) {
        Map<String, String> state = states.get(AddonStateRepository.safeAddonId(addonId));
        if (state == null) {
            return Map.of();
        }
        synchronized (state) {
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> put(String addonId, String key, String value) {
        Map<String, String> values = new java.util.HashMap<>();
        values.put(key, value);
        return put(addonId, values);
    }

    @Override
    public Map<String, String> put(String addonId, Map<String, String> values) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> state = states.computeIfAbsent(safeAddonId, _ignored -> new ConcurrentHashMap<>());
        synchronized (state) {
            Map<String, String> safeValues = safeValues(values);
            if (state.size() + safeValues.keySet().stream().filter(key -> !state.containsKey(key)).count() > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon state key limit reached");
            }
            state.putAll(safeValues);
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> remove(String addonId, String key) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> state = states.get(safeAddonId);
        if (state == null || key == null || key.isBlank()) {
            return list(addonId);
        }
        synchronized (state) {
            state.remove(AddonStateRepository.safeKey(key));
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> removePrefix(String addonId, String keyPrefix) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> state = states.get(safeAddonId);
        if (state == null || keyPrefix == null || keyPrefix.isBlank()) {
            return list(addonId);
        }
        String safePrefix = keyPrefix.trim();
        synchronized (state) {
            state.keySet().removeIf(key -> key.startsWith(safePrefix));
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> replacePrefix(String addonId, String keyPrefix, Map<String, String> values) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> state = states.computeIfAbsent(safeAddonId, _ignored -> new ConcurrentHashMap<>());
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return Map.copyOf(state);
        }
        String safePrefix = keyPrefix.trim();
        Map<String, String> safeValues = safeValues(values);
        synchronized (state) {
            Map<String, String> next = new ConcurrentHashMap<>(state);
            next.keySet().removeIf(key -> key.startsWith(safePrefix));
            if (next.size() + safeValues.keySet().stream().filter(key -> !next.containsKey(key)).count() > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon state key limit reached");
            }
            next.putAll(safeValues);
            state.clear();
            state.putAll(next);
            return Map.copyOf(state);
        }
    }

    @Override
    public void clear(String addonId) {
        states.remove(AddonStateRepository.safeAddonId(addonId));
    }

    @Override
    public Map<String, String> listIsland(String addonId, UUID islandId) {
        Map<String, String> state = islandStates.get(islandStateId(addonId, islandId));
        if (state == null) {
            return Map.of();
        }
        synchronized (state) {
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> putIsland(String addonId, UUID islandId, String key, String value) {
        Map<String, String> values = new java.util.HashMap<>();
        values.put(key, value);
        return putIsland(addonId, islandId, values);
    }

    @Override
    public Map<String, String> putIsland(String addonId, UUID islandId, Map<String, String> values) {
        String stateId = islandStateId(addonId, islandId);
        Map<String, String> state = islandStates.computeIfAbsent(stateId, _ignored -> new ConcurrentHashMap<>());
        synchronized (state) {
            Map<String, String> safeValues = safeValues(values);
            if (state.size() + safeValues.keySet().stream().filter(key -> !state.containsKey(key)).count() > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon island state key limit reached");
            }
            state.putAll(safeValues);
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> removeIsland(String addonId, UUID islandId, String key) {
        String stateId = islandStateId(addonId, islandId);
        Map<String, String> state = islandStates.get(stateId);
        if (state == null || key == null || key.isBlank()) {
            return listIsland(addonId, islandId);
        }
        synchronized (state) {
            state.remove(AddonStateRepository.safeKey(key));
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> removeIslandPrefix(String addonId, UUID islandId, String keyPrefix) {
        String stateId = islandStateId(addonId, islandId);
        Map<String, String> state = islandStates.get(stateId);
        if (state == null || keyPrefix == null || keyPrefix.isBlank()) {
            return listIsland(addonId, islandId);
        }
        String safePrefix = keyPrefix.trim();
        synchronized (state) {
            state.keySet().removeIf(key -> key.startsWith(safePrefix));
            return Map.copyOf(state);
        }
    }

    @Override
    public Map<String, String> replaceIslandPrefix(String addonId, UUID islandId, String keyPrefix, Map<String, String> values) {
        String stateId = islandStateId(addonId, islandId);
        Map<String, String> state = islandStates.computeIfAbsent(stateId, _ignored -> new ConcurrentHashMap<>());
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return Map.copyOf(state);
        }
        String safePrefix = keyPrefix.trim();
        Map<String, String> safeValues = safeValues(values);
        synchronized (state) {
            Map<String, String> next = new ConcurrentHashMap<>(state);
            next.keySet().removeIf(key -> key.startsWith(safePrefix));
            if (next.size() + safeValues.keySet().stream().filter(key -> !next.containsKey(key)).count() > AddonStateRepository.MAX_KEYS_PER_ADDON) {
                throw new IllegalArgumentException("Addon island state key limit reached");
            }
            next.putAll(safeValues);
            state.clear();
            state.putAll(next);
            return Map.copyOf(state);
        }
    }

    @Override
    public void clearIsland(String addonId, UUID islandId) {
        islandStates.remove(islandStateId(addonId, islandId));
    }

    private String islandStateId(String addonId, UUID islandId) {
        return AddonStateRepository.safeAddonId(addonId) + "/" + AddonStateRepository.safeIslandId(islandId);
    }

    private Map<String, String> safeValues(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                safe.put(AddonStateRepository.safeKey(key), AddonStateRepository.safeValue(value));
            }
        });
        return Map.copyOf(safe);
    }
}
