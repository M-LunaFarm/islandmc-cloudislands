package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkBlockValueCommandClient implements BlockValueCommandClient {
    private final JdkCoreApiClient core;

    JdkBlockValueCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<BlockValueActionView> set(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        UUID safeActor = actorUuid == null ? new UUID(0L, 0L) : actorUuid;
        String safeMaterial = requireMaterialKey(materialKey);
        return core.postResultBody("/v1/admin/block-values", CoreJsonPayload.object("actorUuid", safeActor, "materialKey", safeMaterial, "worth", worth == null ? "0" : worth, "levelPoints", levelPoints, "limit", limit))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> CoreBlockValueJson.action(body, safeMaterial));
    }

    private static String requireMaterialKey(String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        return materialKey.trim();
    }
}
