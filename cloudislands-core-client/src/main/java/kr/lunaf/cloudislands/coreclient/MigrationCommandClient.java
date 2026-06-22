package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;

public interface MigrationCommandClient {
    CompletableFuture<MigrationRunSnapshot> migrateSuperiorSkyblock2(String action, String path);
}
