package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CorePermissionCommandClient implements PermissionCommandClient {
    private final CoreApiClient delegate;

    public CorePermissionCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<MutationResult<PermissionMatrixView>> updatePermissions(UpdatePermissionsRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request is required"));
        }
        List<UpdatePermissionsRequest.Change> changes = request.changes();
        if (changes.isEmpty()) {
            PermissionMatrixView empty = new PermissionMatrixView("", List.of());
            return CompletableFuture.completedFuture(new MutationResult<>(empty, empty.version(), false));
        }
        CompletableFuture<MutationResult<PermissionMatrixView>> chain = CompletableFuture.completedFuture(
            new MutationResult<>(new PermissionMatrixView("", List.of()), "", false)
        );
        for (UpdatePermissionsRequest.Change change : changes) {
            chain = chain.thenCompose(previous -> {
                String expectedVersion = previous.version().isBlank() ? change.expectedVersion() : previous.version();
                return delegate.setIslandPermissionResult(
                    request.islandId(),
                    request.actorUuid(),
                    change.roleId().value(),
                    change.permission(),
                    change.allowed(),
                    expectedVersion
                ).thenApply(CorePermissionCommandClient::mutationResult);
            });
        }
        return chain;
    }

    private static MutationResult<PermissionMatrixView> mutationResult(String body) {
        CoreGuiViews.PermissionRulesView rules = CoreGuiViews.permissionRulesView(body);
        PermissionMatrixView view = new PermissionMatrixView(rules.version(), rules.rules());
        return new MutationResult<>(view, view.version(), true);
    }
}
