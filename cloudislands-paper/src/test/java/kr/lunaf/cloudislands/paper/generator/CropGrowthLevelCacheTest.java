package kr.lunaf.cloudislands.paper.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.ProgressionQueryClient;
import org.junit.jupiter.api.Test;

class CropGrowthLevelCacheTest {
    @Test
    void ignoresResponseFromRequestStartedBeforeInvalidation() {
        CompletableFuture<List<CoreGuiViews.UpgradeView>> staleResponse = new CompletableFuture<>();
        CompletableFuture<List<CoreGuiViews.UpgradeView>> currentResponse = new CompletableFuture<>();
        Queue<CompletableFuture<List<CoreGuiViews.UpgradeView>>> responses = new ArrayDeque<>(List.of(staleResponse, currentResponse));
        ProgressionQueryClient progression = (ProgressionQueryClient) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {ProgressionQueryClient.class},
            (proxy, method, args) -> method.getName().equals("upgrades") ? responses.remove() : unsupported(method.getName())
        );
        CoreApiClient client = (CoreApiClient) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (proxy, method, args) -> method.getName().equals("progression") ? progression : unsupported(method.getName())
        );
        CropGrowthLevelCache cache = new CropGrowthLevelCache(client);
        UUID islandId = UUID.randomUUID();

        assertEquals(1, cache.level(islandId));
        cache.invalidate(islandId);
        assertEquals(1, cache.level(islandId));

        staleResponse.complete(List.of(new CoreGuiViews.UpgradeView("crop", "LEVEL", 9, "")));
        assertEquals(1, cache.level(islandId));

        currentResponse.complete(List.of(new CoreGuiViews.UpgradeView("crop", "LEVEL", 3, "")));
        assertEquals(3, cache.level(islandId));
    }

    private static Object unsupported(String methodName) {
        throw new UnsupportedOperationException(methodName);
    }
}
