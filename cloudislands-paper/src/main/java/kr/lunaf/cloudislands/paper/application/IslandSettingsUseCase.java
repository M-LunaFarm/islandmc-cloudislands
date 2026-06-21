package kr.lunaf.cloudislands.paper.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandSettingsCommandClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.IslandSettingsCommandClient;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;

public final class IslandSettingsUseCase {
    private final CoreApiClient coreApiClient;
    private final IslandEnvironmentQueryClient environmentQueries;
    private final IslandSettingsCommandClient settingsCommands;

    public IslandSettingsUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = new CoreIslandEnvironmentQueryClient(coreApiClient);
        this.settingsCommands = new CoreIslandSettingsCommandClient(coreApiClient);
    }

    IslandSettingsUseCase(CoreApiClient coreApiClient, IslandEnvironmentQueryClient environmentQueries) {
        this(coreApiClient, environmentQueries, new CoreIslandSettingsCommandClient(coreApiClient));
    }

    IslandSettingsUseCase(CoreApiClient coreApiClient, IslandEnvironmentQueryClient environmentQueries, IslandSettingsCommandClient settingsCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (environmentQueries == null) {
            throw new IllegalArgumentException("environmentQueries is required");
        }
        if (settingsCommands == null) {
            throw new IllegalArgumentException("settingsCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.environmentQueries = environmentQueries;
        this.settingsCommands = settingsCommands;
    }

    private CompletableFuture<SettingsActionView> setPublicAccessBody(UUID islandId, UUID actorUuid, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.public-access.set", () -> settingsCommands.setPublicAccess(islandId, actorUuid, publicAccess));
    }

    public CompletableFuture<SettingsActionResult> setPublicAccessAction(UUID islandId, UUID actorUuid, boolean publicAccess, MutationRunner runner) {
        return setPublicAccessBody(islandId, actorUuid, publicAccess, runner)
            .thenApply(IslandSettingsUseCase::actionResult);
    }

    private CompletableFuture<SettingsActionView> setLockedBody(UUID islandId, UUID actorUuid, boolean locked, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.locked.set", () -> settingsCommands.setLocked(islandId, actorUuid, locked));
    }

    public CompletableFuture<SettingsActionResult> setLockedAction(UUID islandId, UUID actorUuid, boolean locked, MutationRunner runner) {
        return setLockedBody(islandId, actorUuid, locked, runner)
            .thenApply(IslandSettingsUseCase::actionResult);
    }

    private CompletableFuture<SettingsActionView> setNameBody(UUID islandId, UUID actorUuid, String name, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.name.set", () -> settingsCommands.setName(islandId, actorUuid, name == null ? "" : name));
    }

    public CompletableFuture<SettingsActionResult> setNameAction(UUID islandId, UUID actorUuid, String name, MutationRunner runner) {
        return setNameBody(islandId, actorUuid, name, runner)
            .thenApply(IslandSettingsUseCase::actionResult);
    }

    public CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        requireIsland(islandId);
        return environmentQueries.flagValues(islandId);
    }

    private CompletableFuture<SettingsActionView> setFlagBody(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireFlag(flag);
        requireRunner(runner);
        return runner.mutate("island.flag.set", () -> settingsCommands.setFlag(islandId, actorUuid, flag, value == null ? "" : value));
    }

    public CompletableFuture<SettingsActionResult> setFlagAction(UUID islandId, UUID actorUuid, IslandFlag flag, String value, MutationRunner runner) {
        return setFlagBody(islandId, actorUuid, flag, value, runner)
            .thenApply(IslandSettingsUseCase::actionResult);
    }

    private static SettingsActionResult actionResult(SettingsActionView view) {
        return new SettingsActionResult(view.accepted(), view.code());
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
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record SettingsActionResult(boolean accepted, String code) {
        public SettingsActionResult {
            code = code == null ? "" : code;
        }
    }
}
