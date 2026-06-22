package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;

public final class JdkMigrationCommandClient implements MigrationCommandClient {
    private final JdkCoreApiClient core;

    public JdkMigrationCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<MigrationRunSnapshot> migrateSuperiorSkyblock2(String action, String path) {
        String normalizedAction = action == null || action.isBlank() ? "scan" : action.toLowerCase();
        String endpoint = switch (normalizedAction) {
            case "scan" -> "scan";
            case "status" -> "status";
            case "dryrun", "dry-run" -> "dryrun";
            case "extract", "extract-worlds", "world-extract" -> "extract";
            case "import" -> "import";
            case "verify" -> "verify";
            case "rollback" -> "rollback";
            default -> "";
        };
        if (endpoint.isBlank()) {
            return CompletableFuture.completedFuture(new MigrationRunSnapshot(
                "INVALID_MIGRATION_ACTION",
                path == null ? "" : path,
                0,
                false,
                false,
                0,
                false,
                0,
                false,
                0,
                List.of(new MigrationIssueSnapshot("INVALID_MIGRATION_ACTION", "Unknown SuperiorSkyblock2 migration action: " + normalizedAction, true))
            ));
        }
        String value = path == null ? "" : path;
        String payload = endpoint.equals("import")
            ? CoreJsonPayload.object("approval", value)
            : (endpoint.equals("rollback") || endpoint.equals("status")) ? "{}" : CoreJsonPayload.object("path", value);
        return core.postWithResultBody("/v1/admin/migrations/superiorskyblock2/" + endpoint, payload)
            .thenApply(CoreMigrationJson::run);
    }
}
