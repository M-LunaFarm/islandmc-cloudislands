package kr.lunaf.cloudislands.paper.world.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBackedCellTransferTest {
    @TempDir
    Path root;

    @Test
    void placementReplacesTheCompleteTargetRegionSetWithoutKeepingOldIslandFiles() throws Exception {
        Path source = root.resolve("bundle/chunks");
        Path worldRegion = root.resolve("worlds/ci_shard_001/region");
        Files.createDirectories(source);
        Files.createDirectories(worldRegion);
        Files.writeString(source.resolve("r.0.0.mca"), "new-island", StandardCharsets.UTF_8);
        Files.writeString(worldRegion.resolve("r.0.0.mca"), "old-island", StandardCharsets.UTF_8);
        Files.writeString(worldRegion.resolve("r.-1.-1.mca"), "stale-old-island", StandardCharsets.UTF_8);
        Files.writeString(worldRegion.resolve("r.3.3.mca"), "other-cell", StandardCharsets.UTF_8);

        new FileBackedCellTransfer(root.resolve("worlds")).place(plan(source));

        assertEquals("new-island", Files.readString(worldRegion.resolve("r.0.0.mca")));
        assertFalse(Files.exists(worldRegion.resolve("r.-1.-1.mca")), "bundle-absent region files in the target cell must be removed");
        assertEquals("other-cell", Files.readString(worldRegion.resolve("r.3.3.mca")), "regions outside the target cell must stay untouched");
    }

    @Test
    void clearRemovesChunkEntityAndPoiFilesOnlyInsideTheReusedCell() throws Exception {
        Path world = root.resolve("worlds/ci_shard_001");
        for (String dataSet : java.util.List.of("region", "entities", "poi")) {
            Files.createDirectories(world.resolve(dataSet));
            Files.writeString(world.resolve(dataSet + "/r.-1.-1.mca"), "stale-cell");
            Files.writeString(world.resolve(dataSet + "/r.3.3.mca"), "neighbor-cell");
        }

        new FileBackedCellTransfer(root.resolve("worlds")).clear("ci_shard_001", -2, 2, -2, 2);

        for (String dataSet : java.util.List.of("region", "entities", "poi")) {
            assertFalse(Files.exists(world.resolve(dataSet + "/r.-1.-1.mca")));
            assertEquals("neighbor-cell", Files.readString(world.resolve(dataSet + "/r.3.3.mca")));
        }
    }

    @Test
    void mismatchedBundleCoordinatesFailBeforeChangingTheTargetCell() throws Exception {
        Path source = root.resolve("mismatched/chunks");
        Path worldRegion = root.resolve("worlds/ci_shard_001/region");
        Files.createDirectories(source);
        Files.createDirectories(worldRegion);
        Files.writeString(source.resolve("r.2.2.mca"), "wrong-cell", StandardCharsets.UTF_8);
        Files.writeString(worldRegion.resolve("r.0.0.mca"), "existing-island", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> new FileBackedCellTransfer(root.resolve("worlds")).place(plan(source)));

        assertTrue(exception.getMessage().contains("do not match target cell"));
        assertEquals("existing-island", Files.readString(worldRegion.resolve("r.0.0.mca")));
    }

    @Test
    void failedReplacementRollsBackFilesAlreadyChangedInTheTargetCell() throws Exception {
        Path source = root.resolve("rollback/chunks");
        Path worldRegion = root.resolve("worlds/ci_shard_001/region");
        Files.createDirectories(source);
        Files.createDirectories(worldRegion);
        Files.writeString(source.resolve("r.-1.-1.mca"), "replacement", StandardCharsets.UTF_8);
        Files.writeString(worldRegion.resolve("r.-1.-1.mca"), "original", StandardCharsets.UTF_8);
        Files.createDirectory(worldRegion.resolve("r.0.0.mca"));

        assertThrows(IOException.class, () -> new FileBackedCellTransfer(root.resolve("worlds")).place(plan(source)));

        assertEquals("original", Files.readString(worldRegion.resolve("r.-1.-1.mca")));
        assertTrue(Files.isDirectory(worldRegion.resolve("r.0.0.mca")));
    }

    @Test
    void poiPublishFailureRollsBackAlreadyPublishedChunkAndEntityRegions() throws Exception {
        Path source = root.resolve("multi/chunks");
        Path sourceEntities = root.resolve("multi/entities");
        Path world = root.resolve("worlds/ci_shard_001");
        Files.createDirectories(source);
        Files.createDirectories(sourceEntities);
        Files.createDirectories(world.resolve("region"));
        Files.createDirectories(world.resolve("entities"));
        Files.createDirectories(world.resolve("poi"));
        Files.writeString(source.resolve("r.-1.-1.mca"), "new-chunks");
        Files.writeString(sourceEntities.resolve("r.-1.-1.mca"), "new-entities");
        Files.writeString(world.resolve("region/r.-1.-1.mca"), "old-chunks");
        Files.writeString(world.resolve("entities/r.-1.-1.mca"), "old-entities");
        Files.createDirectory(world.resolve("poi/r.0.0.mca"));

        assertThrows(IOException.class, () -> new FileBackedCellTransfer(root.resolve("worlds")).place(plan(source)));

        assertEquals("old-chunks", Files.readString(world.resolve("region/r.-1.-1.mca")));
        assertEquals("old-entities", Files.readString(world.resolve("entities/r.-1.-1.mca")));
        assertTrue(Files.isDirectory(world.resolve("poi/r.0.0.mca")));
    }

    private CellPlacementPlan plan(Path source) {
        return new CellPlacementPlan(
            UUID.fromString("00000000-0000-0000-0000-000000001301"),
            "ci_shard_001",
            0,
            0,
            source,
            -2,
            2,
            -2,
            2
        );
    }
}
