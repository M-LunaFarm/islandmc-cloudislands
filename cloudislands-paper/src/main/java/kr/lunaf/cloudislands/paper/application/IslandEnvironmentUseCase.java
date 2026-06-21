package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LimitView;

public final class IslandEnvironmentUseCase {
    private final CoreApiClient coreApiClient;

    public IslandEnvironmentUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<BiomeValue> islandBiomeValue(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandBiome(coreApiClient, islandId).thenApply(BiomeValue::new);
    }

    private CompletableFuture<String> setBiomeBody(UUID islandId, UUID actorUuid, String biomeKey, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.biome.set", () -> coreApiClient.setIslandBiomeResult(islandId, actorUuid, biomeKey == null ? "" : biomeKey));
    }

    public CompletableFuture<EnvironmentActionResult> setBiomeAction(UUID islandId, UUID actorUuid, String biomeKey, MutationRunner runner) {
        return setBiomeBody(islandId, actorUuid, biomeKey, runner)
            .thenApply(body -> actionResult(body, "BIOME_SET"));
    }

    public CompletableFuture<IslandInfoView> islandInfoView(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandInfo(coreApiClient, islandId);
    }

    public CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandFlags(coreApiClient, islandId);
    }

    private CompletableFuture<String> setFlagBody(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireFlag(flag);
        requireRunner(runner);
        return runner.mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value));
    }

    public CompletableFuture<EnvironmentActionResult> setFlagAction(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        return setFlagBody(islandId, actorUuid, flag, value, runner)
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    public CompletableFuture<List<LimitView>> limitViews(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandLimits(coreApiClient, islandId);
    }

    private CompletableFuture<String> setLimitBody(UUID islandId, UUID actorUuid, String limitKey, long value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.limit.set", () -> coreApiClient.setIslandLimit(islandId, actorUuid, limitKey == null ? "" : limitKey, value));
    }

    public CompletableFuture<EnvironmentActionResult> setLimitAction(UUID islandId, UUID actorUuid, String limitKey, long value, MutationRunner runner) {
        return setLimitBody(islandId, actorUuid, limitKey, value, runner)
            .thenApply(body -> actionResult(body, "LIMIT_SET"));
    }

    private static EnvironmentActionResult actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted", true);
        accepted = accepted && !root.containsKey("error") && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new EnvironmentActionResult(
            accepted,
            code,
            firstText(root, "limitKey", "biomeKey", "flag", "key"),
            SimpleJson.number(root.get("value"))
        );
    }

    private static String firstText(Map<?, ?> root, String... keys) {
        for (String key : keys) {
            String value = text(root, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Map<?, ?> root, String key) {
        return SimpleJson.text(root.get(key));
    }

    private static boolean bool(Map<?, ?> root, String key, boolean fallback) {
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireFlag(IslandFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("flag is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record EnvironmentActionResult(boolean accepted, String code, String key, long value) {
        public EnvironmentActionResult {
            code = code == null ? "" : code;
            key = key == null ? "" : key;
        }
    }

    public record BiomeValue(String key) {
        public BiomeValue {
            key = key == null ? "" : key;
        }
    }
}
