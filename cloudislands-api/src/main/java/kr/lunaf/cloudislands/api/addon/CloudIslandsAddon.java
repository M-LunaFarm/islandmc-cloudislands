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

    default void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
    }

    default void onAddonReloaded(CloudIslandsAddonSnapshot snapshot) {
        onAddonRegistered(snapshot);
    }

    default void onAddonUnregistered() {
    }

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsApi api) {
        return api.addons().register(this);
    }

    default CompletableFuture<Void> unregister(CloudIslandsApi api) {
        return api.addons().unregister(addonId());
    }
}
