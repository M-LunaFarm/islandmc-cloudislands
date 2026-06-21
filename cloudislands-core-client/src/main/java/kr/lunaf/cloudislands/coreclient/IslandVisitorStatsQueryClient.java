package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IslandVisitorStatsQueryClient {
    CompletableFuture<IslandVisitorStatsView> stats(UUID islandId, int recentLimit);
}
