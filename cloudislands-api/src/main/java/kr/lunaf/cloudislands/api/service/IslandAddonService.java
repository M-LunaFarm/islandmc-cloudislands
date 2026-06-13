package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public interface IslandAddonService {
    CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled);
    CompletableFuture<Void> unregister(String id);
    CompletableFuture<Optional<CloudIslandsAddonSnapshot>> get(String id);
    CompletableFuture<List<CloudIslandsAddonSnapshot>> list();
    CompletableFuture<Boolean> isEnabled(String id);
}
