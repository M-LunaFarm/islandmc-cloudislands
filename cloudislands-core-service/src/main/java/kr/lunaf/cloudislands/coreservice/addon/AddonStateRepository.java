package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;

public interface AddonStateRepository {
    int MAX_ADDON_ID_LENGTH = 128;
    int MAX_KEY_LENGTH = 128;
    int MAX_VALUE_LENGTH = 4096;
    int MAX_KEYS_PER_ADDON = 128;

    Map<String, String> list(String addonId);
    Map<String, String> put(String addonId, String key, String value);
    Map<String, String> remove(String addonId, String key);
    void clear(String addonId);

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

    static void requireKeyCapacity(Map<String, String> state, String key) {
        if (state != null && !state.containsKey(key) && state.size() >= MAX_KEYS_PER_ADDON) {
            throw new IllegalArgumentException("Addon state key limit reached");
        }
    }
}
