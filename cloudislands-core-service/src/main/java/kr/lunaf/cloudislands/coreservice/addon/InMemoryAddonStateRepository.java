package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAddonStateRepository implements AddonStateRepository {
    private final Map<String, Map<String, String>> states = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> list(String addonId) {
        return Map.copyOf(states.getOrDefault(AddonStateRepository.safeAddonId(addonId), Map.of()));
    }

    @Override
    public Map<String, String> put(String addonId, String key, String value) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        String safeKey = AddonStateRepository.safeKey(key);
        String safeValue = AddonStateRepository.safeValue(value);
        Map<String, String> state = states.computeIfAbsent(safeAddonId, _ignored -> new ConcurrentHashMap<>());
        AddonStateRepository.requireKeyCapacity(state, safeKey);
        state.put(safeKey, safeValue);
        return Map.copyOf(state);
    }

    @Override
    public Map<String, String> remove(String addonId, String key) {
        String safeAddonId = AddonStateRepository.safeAddonId(addonId);
        Map<String, String> state = states.get(safeAddonId);
        if (state == null || key == null || key.isBlank()) {
            return list(addonId);
        }
        state.remove(AddonStateRepository.safeKey(key));
        return Map.copyOf(state);
    }

    @Override
    public void clear(String addonId) {
        states.remove(AddonStateRepository.safeAddonId(addonId));
    }
}
