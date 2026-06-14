package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;
import java.util.UUID;

public interface AddonStateRepository {
    int MAX_ADDON_ID_LENGTH = 128;
    int MAX_KEY_LENGTH = 128;
    int MAX_VALUE_LENGTH = 65535;
    int MAX_KEYS_PER_ADDON = 4096;

    Map<String, String> list(String addonId);
    Map<String, String> put(String addonId, String key, String value);
    Map<String, String> put(String addonId, Map<String, String> values);
    Map<String, String> remove(String addonId, String key);
    Map<String, String> removePrefix(String addonId, String keyPrefix);
    Map<String, String> replacePrefix(String addonId, String keyPrefix, Map<String, String> values);
    void clear(String addonId);
    Map<String, String> listIsland(String addonId, UUID islandId);
    Map<String, String> putIsland(String addonId, UUID islandId, String key, String value);
    Map<String, String> putIsland(String addonId, UUID islandId, Map<String, String> values);
    Map<String, String> removeIsland(String addonId, UUID islandId, String key);
    Map<String, String> removeIslandPrefix(String addonId, UUID islandId, String keyPrefix);
    Map<String, String> replaceIslandPrefix(String addonId, UUID islandId, String keyPrefix, Map<String, String> values);
    void clearIsland(String addonId, UUID islandId);

    static String safeAddonId(String addonId) {
        String value = addonId == null ? "" : addonId.trim();
        if (value.isBlank()) {
            value = "unknown-addon";
        }
        if (value.length() > MAX_ADDON_ID_LENGTH) {
            throw new IllegalArgumentException("Addon id is too long");
        }
        return value;
    }

    static String safeKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state key is required");
        }
        if (value.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state key is too long");
        }
        return value;
    }

    static String safeValue(String value) {
        String safe = value == null ? "" : value;
        if (safe.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Addon state value is too long");
        }
        return safe;
    }

    static UUID safeIslandId(UUID islandId) {
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Island id is required");
        }
        return islandId;
    }

    static void requireKeyCapacity(Map<String, String> state, String key) {
        if (state != null && !state.containsKey(key) && state.size() >= MAX_KEYS_PER_ADDON) {
            throw new IllegalArgumentException("Addon state key limit reached");
        }
    }
}
