package kr.lunaf.cloudislands.paper.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;

public final class IslandSettingsUseCase {
    private final CoreApiClient coreApiClient;
    private final IslandEnvironmentQueryClient environmentQueries;

    public IslandSettingsUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = new CoreIslandEnvironmentQueryClient(coreApiClient);
    }

    IslandSettingsUseCase(CoreApiClient coreApiClient, IslandEnvironmentQueryClient environmentQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (environmentQueries == null) {
            throw new IllegalArgumentException("environmentQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = environmentQueries;
    }

    private CompletableFuture<String> setPublicAccessBody(UUID islandId, UUID actorUuid, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.public-access.set", () -> coreApiClient.setIslandPublicAccessResult(islandId, actorUuid, publicAccess));
    }

    public CompletableFuture<SettingsActionResult> setPublicAccessAction(UUID islandId, UUID actorUuid, boolean publicAccess, MutationRunner runner) {
        return setPublicAccessBody(islandId, actorUuid, publicAccess, runner)
            .thenApply(body -> actionResult(body, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED"));
    }

    private CompletableFuture<String> setLockedBody(UUID islandId, UUID actorUuid, boolean locked, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.locked.set", () -> coreApiClient.setIslandLockedResult(islandId, actorUuid, locked));
    }

    public CompletableFuture<SettingsActionResult> setLockedAction(UUID islandId, UUID actorUuid, boolean locked, MutationRunner runner) {
        return setLockedBody(islandId, actorUuid, locked, runner)
            .thenApply(body -> actionResult(body, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED"));
    }

    private CompletableFuture<String> setNameBody(UUID islandId, UUID actorUuid, String name, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.name.set", () -> coreApiClient.setIslandNameResult(islandId, actorUuid, name == null ? "" : name));
    }

    public CompletableFuture<SettingsActionResult> setNameAction(UUID islandId, UUID actorUuid, String name, MutationRunner runner) {
        return setNameBody(islandId, actorUuid, name, runner)
            .thenApply(body -> actionResult(body, "ISLAND_RENAMED"));
    }

    public CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.flagValues(islandId);
    }

    private CompletableFuture<String> setFlagBody(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireFlag(flag);
        requireRunner(runner);
        return runner.mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value));
    }

    public CompletableFuture<SettingsActionResult> setFlagAction(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        return setFlagBody(islandId, actorUuid, flag, value, runner)
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    private static SettingsActionResult actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new SettingsActionResult(accepted, code);
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

    public record SettingsActionResult(boolean accepted, String code) {
        public SettingsActionResult {
            code = code == null ? "" : code;
        }
    }
}
