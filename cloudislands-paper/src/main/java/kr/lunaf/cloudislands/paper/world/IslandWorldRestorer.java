package kr.lunaf.cloudislands.paper.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlan;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class IslandWorldRestorer {
    private final IslandStorage storage;
    private final Path stagingRoot;
    private final BundleRestorePlanner restorePlanner;

    public IslandWorldRestorer(IslandStorage storage, Path stagingRoot, BundleRestorePlanner restorePlanner) {
        this.storage = storage;
        this.stagingRoot = stagingRoot;
        this.restorePlanner = restorePlanner;
    }

    public BundleRestorePlan stage(UUID islandId, String worldName, int originX, int originZ) throws IOException {
        Path islandStage = stagingRoot.resolve(islandId.toString());
        Files.createDirectories(islandStage);
        Path bundle = islandStage.resolve("bundle.tar.zst");
        try (InputStream input = storage.openLatestBundle(islandId)) {
            Files.copy(input, bundle, StandardCopyOption.REPLACE_EXISTING);
        }
        RestorePlan restorePlan = new RestorePlan(islandId, worldName, originX, originZ, bundle);
        return restorePlanner.plan(restorePlan);
    }

    public record RestorePlan(UUID islandId, String worldName, int originX, int originZ, Path stagedBundle) {}
}
