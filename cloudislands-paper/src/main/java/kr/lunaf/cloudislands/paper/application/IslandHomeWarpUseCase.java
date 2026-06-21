package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreHomeWarpQueryClient;
import kr.lunaf.cloudislands.coreclient.HomeWarpQueryClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.HomeView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.WarpView;

public final class IslandHomeWarpUseCase {
    private final CoreApiClient coreApiClient;
    private final HomeWarpQueryClient homeWarpQueries;

    public IslandHomeWarpUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.homeWarpQueries = new CoreHomeWarpQueryClient(coreApiClient);
    }

    IslandHomeWarpUseCase(CoreApiClient coreApiClient, HomeWarpQueryClient homeWarpQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (homeWarpQueries == null) {
            throw new IllegalArgumentException("homeWarpQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.homeWarpQueries = homeWarpQueries;
    }

    private CompletableFuture<String> setHomeBody(UUID islandId, UUID actorUuid, String name, IslandLocation location, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireLocation(location);
        requireRunner(runner);
        return runner.mutate("island.home.set", () -> coreApiClient.setIslandHomeResult(islandId, actorUuid, normalizeName(name), location));
    }

    public CompletableFuture<HomeWarpActionResult> setHomeAction(UUID islandId, UUID actorUuid, String name, IslandLocation location, MutationRunner runner) {
        return setHomeBody(islandId, actorUuid, name, location, runner)
            .thenApply(body -> actionResult(body, "HOME_SET"));
    }

    private CompletableFuture<String> setWarpBody(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireLocation(location);
        requireRunner(runner);
        return runner.mutate("island.warp.set", () -> coreApiClient.setIslandWarpResult(islandId, actorUuid, normalizeName(name), location, publicAccess));
    }

    public CompletableFuture<HomeWarpActionResult> setWarpAction(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, MutationRunner runner) {
        return setWarpBody(islandId, actorUuid, name, location, publicAccess, runner)
            .thenApply(body -> actionResult(body, "WARP_SET"));
    }

    public CompletableFuture<List<HomeView>> homeViews(UUID islandId) {
        requireIsland(islandId);
        return homeWarpQueries.homes(islandId).thenApply(views -> views.stream().map(IslandHomeWarpUseCase::homeView).toList());
    }

    public CompletableFuture<List<WarpView>> warpViews(UUID islandId) {
        requireIsland(islandId);
        return homeWarpQueries.warps(islandId).thenApply(views -> views.stream().map(IslandHomeWarpUseCase::warpView).toList());
    }

    public CompletableFuture<IslandInfoView> islandInfoView(UUID islandId) {
        requireIsland(islandId);
        return homeWarpQueries.islandInfo(islandId).thenApply(IslandHomeWarpUseCase::islandInfoView);
    }

    private CompletableFuture<String> deleteWarpBody(UUID islandId, UUID actorUuid, String name, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.warp.delete", () -> coreApiClient.deleteIslandWarpResult(islandId, actorUuid, normalizeName(name)));
    }

    public CompletableFuture<HomeWarpActionResult> deleteWarpAction(UUID islandId, UUID actorUuid, String name, IdempotentMutationRunner runner) {
        return deleteWarpBody(islandId, actorUuid, name, runner)
            .thenApply(body -> actionResult(body, "WARP_DELETED"));
    }

    private CompletableFuture<String> setWarpPublicAccessBody(UUID islandId, UUID actorUuid, String name, boolean publicAccess, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.warp.public-access.set", () -> coreApiClient.setIslandWarpPublicAccessResult(islandId, actorUuid, normalizeName(name), publicAccess));
    }

    public CompletableFuture<HomeWarpActionResult> setWarpPublicAccessAction(UUID islandId, UUID actorUuid, String name, boolean publicAccess, MutationRunner runner) {
        return setWarpPublicAccessBody(islandId, actorUuid, name, publicAccess, runner)
            .thenApply(body -> actionResult(body, publicAccess ? "WARP_PUBLIC" : "WARP_PRIVATE"));
    }

    public CompletableFuture<List<WarpView>> publicWarpViews(int limit, String category, String query) {
        return homeWarpQueries.publicWarps(limit, category, query).thenApply(views -> views.stream().map(IslandHomeWarpUseCase::warpView).toList());
    }

    private static HomeView homeView(CoreGuiViews.HomeView view) {
        return new HomeView(view.name(), view.x(), view.y(), view.z(), view.createdAt());
    }

    private static WarpView warpView(CoreGuiViews.WarpView view) {
        return new WarpView(view.islandId(), view.name(), view.x(), view.y(), view.z(), view.publicAccess(), view.category());
    }

    private static IslandInfoView islandInfoView(CoreGuiViews.IslandInfoView view) {
        return new IslandInfoView(view.name(), view.state(), view.islandId(), view.level(), view.worth(), view.publicAccess(), view.locked(), view.size(), view.border(), view.ownerUuid());
    }

    private static HomeWarpActionResult actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new HomeWarpActionResult(accepted, code);
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

    private static void requireLocation(IslandLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "default" : name;
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record HomeWarpActionResult(boolean accepted, String code) {
        public HomeWarpActionResult {
            code = code == null ? "" : code;
        }
    }
}
