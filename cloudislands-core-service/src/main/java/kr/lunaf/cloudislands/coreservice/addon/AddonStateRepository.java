package kr.lunaf.cloudislands.coreservice.addon;

import java.util.Map;

public interface AddonStateRepository {
    Map<String, String> list(String addonId);
    Map<String, String> put(String addonId, String key, String value);
    Map<String, String> remove(String addonId, String key);
    void clear(String addonId);
}
