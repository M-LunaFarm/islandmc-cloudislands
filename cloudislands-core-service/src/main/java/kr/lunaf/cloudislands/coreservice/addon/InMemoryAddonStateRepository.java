package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAddonStateRepository implements AddonStateRepository {
    private final Map<String, Map<String, String>> states = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> list(String addonId) {
        return Map.copyOf(states.getOrDefault(safeAddonId(addonId), Map.of()));
    }

    @Override
    public Map<String, String> put(String addonId, String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return list(addonId);
        }
        Map<String, String> state = states.computeIfAbsent(safeAddonId(addonId), _ignored -> new ConcurrentHashMap<>());
        state.put(key, value);
        return Map.copyOf(state);
    }

    @Override
    public Map<String, String> remove(String addonId, String key) {
        Map<String, String> state = states.get(safeAddonId(addonId));
        if (state == null || key == null) {
            return list(addonId);
        }
        state.remove(key);
        return Map.copyOf(state);
    }

    @Override
    public void clear(String addonId) {
        states.remove(safeAddonId(addonId));
    }

    private String safeAddonId(String addonId) {
        return addonId == null || addonId.isBlank() ? "unknown-addon" : addonId;
    }
}
