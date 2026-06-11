package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;

public interface IslandBundleExporter {
    ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory) throws IOException;

    record ExportedIslandBundle(UUID islandId, Path bundleFile, long snapshotNo) {}
}
