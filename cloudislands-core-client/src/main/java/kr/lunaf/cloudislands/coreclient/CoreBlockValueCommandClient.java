package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreBlockValueCommandClient implements BlockValueCommandClient {
    private final CoreApiClient delegate;

    public CoreBlockValueCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<BlockValueActionView> set(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        UUID safeActor = actorUuid == null ? new UUID(0L, 0L) : actorUuid;
        String safeMaterial = requireMaterial(materialKey);
        return delegate.setBlockValueResult(safeActor, safeMaterial, worth == null ? "0" : worth, levelPoints, limit)
            .thenApply(body -> CoreBlockValueJson.action(body, safeMaterial));
    }

    private static String requireMaterial(String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        return materialKey.trim();
    }
}
