package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

final class IslandTargetResolver {
    private final CoreApiClient client;

    IslandTargetResolver(CoreApiClient client) {
        this.client = client;
    }

    CompletableFuture<UUID> resolve(String target) {
        UUID direct = uuid(target);
        if (direct != null) {
            return CompletableFuture.completedFuture(direct);
        }
        String query = target == null ? "" : target.trim();
        if (query.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("island target is blank"));
        }
        return client.islands().findIslandByName(query)
            .handle((island, error) -> error == null ? islandId(island) : null)
            .thenCompose(islandId -> islandId != null
                ? CompletableFuture.completedFuture(islandId)
                : client.navigation().playerProfileByName(query).thenApply(profile -> requiredUuid(profile == null ? "" : profile.primaryIslandId(), query)));
    }

    private static UUID islandId(CoreGuiViews.IslandInfoView island) {
        return island == null ? null : uuid(island.islandId());
    }

    private static UUID requiredUuid(String value, String query) {
        UUID parsed = uuid(value);
        if (parsed == null) {
            throw new IllegalArgumentException("island target was not found: " + query);
        }
        return parsed;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
