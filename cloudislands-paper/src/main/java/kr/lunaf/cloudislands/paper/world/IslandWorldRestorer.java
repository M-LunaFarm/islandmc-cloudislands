package kr.lunaf.cloudislands.paper.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class IslandWorldRestorer {
    private final IslandStorage storage;
    private final Path stagingRoot;

    public IslandWorldRestorer(IslandStorage storage, Path stagingRoot) {
        this.storage = storage;
        this.stagingRoot = stagingRoot;
    }

    public RestorePlan stage(UUID islandId, String worldName, int originX, int originZ) throws IOException {
        Path islandStage = stagingRoot.resolve(islandId.toString());
        Files.createDirectories(islandStage);
        Path bundle = islandStage.resolve("bundle.tar.zst");
        try (InputStream input = storage.openLatestBundle(islandId)) {
            Files.copy(input, bundle, StandardCopyOption.REPLACE_EXISTING);
        }
        return new RestorePlan(islandId, worldName, originX, originZ, bundle);
    }

    public record RestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle) {}
}
