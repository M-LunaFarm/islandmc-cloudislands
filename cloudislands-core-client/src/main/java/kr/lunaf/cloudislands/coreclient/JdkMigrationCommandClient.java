package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;

public final class JdkMigrationCommandClient implements MigrationCommandClient {
    private static final String SUPERIOR_SKYBLOCK2_SCAN = "/v1/admin/migrations/superiorskyblock2/scan";
    private static final String SUPERIOR_SKYBLOCK2_STATUS = "/v1/admin/migrations/superiorskyblock2/status";
    private static final String SUPERIOR_SKYBLOCK2_DRYRUN = "/v1/admin/migrations/superiorskyblock2/dryrun";
    private static final String SUPERIOR_SKYBLOCK2_EXTRACT = "/v1/admin/migrations/superiorskyblock2/extract";
    private static final String SUPERIOR_SKYBLOCK2_IMPORT = "/v1/admin/migrations/superiorskyblock2/import";
    private static final String SUPERIOR_SKYBLOCK2_VERIFY = "/v1/admin/migrations/superiorskyblock2/verify";
    private static final String SUPERIOR_SKYBLOCK2_ROLLBACK = "/v1/admin/migrations/superiorskyblock2/rollback";

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
        String value = path == null ? "" : path;
        return switch (normalizedAction) {
            case "scan" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_SCAN, CoreJsonPayload.object("path", value)));
            case "status" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_STATUS, "{}"));
            case "dryrun", "dry-run" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_DRYRUN, CoreJsonPayload.object("path", value)));
            case "extract", "extract-worlds", "world-extract" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_EXTRACT, CoreJsonPayload.object("path", value)));
            case "import" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_IMPORT, CoreJsonPayload.object("approval", value)));
            case "verify" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_VERIFY, CoreJsonPayload.object("path", value)));
            case "rollback" -> run(core.postResultBody(SUPERIOR_SKYBLOCK2_ROLLBACK, "{}"));
            default -> CompletableFuture.completedFuture(new MigrationRunSnapshot(
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
        };
    }

    private CompletableFuture<MigrationRunSnapshot> run(CompletableFuture<CoreResponseBody> response) {
        return response
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreMigrationJson::run);
    }
}
