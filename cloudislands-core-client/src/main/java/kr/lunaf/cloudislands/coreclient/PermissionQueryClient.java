package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PermissionQueryClient {
    CompletableFuture<List<PermissionAssignmentView>> permissions(UUID islandId);

    CompletableFuture<List<CoreGuiViews.RoleView>> roles(UUID islandId);
}
