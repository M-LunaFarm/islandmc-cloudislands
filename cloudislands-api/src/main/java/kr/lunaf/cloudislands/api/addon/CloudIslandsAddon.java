package kr.lunaf.cloudislands.api.addon;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public interface CloudIslandsAddon {
    String addonId();

    String addonDisplayName();

    String addonVersion();

    default boolean enabledByDefault() {
        return true;
    }

    default Map<String, Boolean> addonFeatures() {
        return Map.of();
    }

    default Map<String, String> addonMetadata() {
        return Map.of();
    }

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsApi api) {
        return api.addons().register(addonId(), addonDisplayName(), addonVersion(), enabledByDefault(), addonFeatures(), addonMetadata());
    }

    default CompletableFuture<Void> unregister(CloudIslandsApi api) {
        return api.addons().unregister(addonId());
    }
}
