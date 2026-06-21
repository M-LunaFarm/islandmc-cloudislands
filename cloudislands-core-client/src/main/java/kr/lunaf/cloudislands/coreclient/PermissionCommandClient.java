package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface PermissionCommandClient {
    CompletableFuture<MutationResult<PermissionMatrixView>> updatePermissions(UpdatePermissionsRequest request);
}
