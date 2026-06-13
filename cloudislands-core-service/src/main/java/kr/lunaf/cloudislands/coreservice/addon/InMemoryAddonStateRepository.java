package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAddonStateRepository implements AddonStateRepository {
    private final Map<String, Map<String, String>> states = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> islandStates = new ConcurrentHashMap<>();

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

    @Override
    public Map<String, String> listIsland(String addonId, UUID islandId) {
        return Map.copyOf(islandStates.getOrDefault(islandStateId(addonId, islandId), Map.of()));
    }

    @Override
    public Map<String, String> putIsland(String addonId, UUID islandId, String key, String value) {
        String stateId = islandStateId(addonId, islandId);
        String safeKey = AddonStateRepository.safeKey(key);
        String safeValue = AddonStateRepository.safeValue(value);
        Map<String, String> state = islandStates.computeIfAbsent(stateId, _ignored -> new ConcurrentHashMap<>());
        AddonStateRepository.requireKeyCapacity(state, safeKey);
        state.put(safeKey, safeValue);
        return Map.copyOf(state);
    }

    @Override
    public Map<String, String> removeIsland(String addonId, UUID islandId, String key) {
        String stateId = islandStateId(addonId, islandId);
        Map<String, String> state = islandStates.get(stateId);
        if (state == null || key == null || key.isBlank()) {
            return listIsland(addonId, islandId);
        }
        state.remove(AddonStateRepository.safeKey(key));
        return Map.copyOf(state);
    }

    @Override
    public void clearIsland(String addonId, UUID islandId) {
        islandStates.remove(islandStateId(addonId, islandId));
    }

    private String islandStateId(String addonId, UUID islandId) {
        return AddonStateRepository.safeAddonId(addonId) + "/" + AddonStateRepository.safeIslandId(islandId);
    }
}
