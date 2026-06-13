package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;

public interface IslandBundleExporter {
    ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory) throws IOException;

    default ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory, IslandBundleManifest manifest) throws IOException {
        return export(islandId, activeIsland, targetDirectory);
    }

    record ExportedIslandBundle(UUID islandId, Path bundleFile, long snapshotNo) {}
}
