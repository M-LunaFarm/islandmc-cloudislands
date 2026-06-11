package kr.lunaf.cloudislands.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;

public interface IslandPermissionService {
    CompletableFuture<PermissionResult> check(UUID playerUuid, UUID islandId, IslandPermission permission);
    CompletableFuture<PermissionResult> checkAt(UUID playerUuid, String worldName, int blockX, int blockY, int blockZ, IslandPermission permission);
}
