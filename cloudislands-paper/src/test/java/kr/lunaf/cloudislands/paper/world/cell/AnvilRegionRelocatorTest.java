package kr.lunaf.cloudislands.paper.world.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.DoubleTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnvilRegionRelocatorTest {
    @TempDir
    Path root;

    @Test
    void placementRelocatesRealModernChunkNbtAcrossPhysicalCells() throws Exception {
        Path source = root.resolve("bundle/chunks");
        Files.createDirectories(source);
        writeRegion(source.resolve("r.0.0.mca"), sampleChunk());

        CellPlacementPlan plan = new CellPlacementPlan(
            UUID.fromString("00000000-0000-0000-0000-000000001302"),
            "ci_shard_001", 1024, 0, source, 62, 66, -2, 2, 0, 0, true
        );
        new FileBackedCellTransfer(root.resolve("worlds")).place(plan);

        Path relocatedFile = root.resolve("worlds/ci_shard_001/region/r.2.0.mca");
        assertTrue(Files.isRegularFile(relocatedFile));
        assertFalse(Files.exists(root.resolve("worlds/ci_shard_001/region/r.0.0.mca")));
        CompoundTag relocated = readChunk(relocatedFile, 64, 0);
        assertEquals(64, relocated.getInt("xPos").orElseThrow());
        assertEquals(0, relocated.getInt("zPos").orElseThrow());

        CompoundTag blockEntity = (CompoundTag) relocated.getListTag("block_entities").get(0);
        assertEquals(1025, blockEntity.getInt("x").orElseThrow());
        CompoundTag tick = (CompoundTag) relocated.getListTag("block_ticks").get(0);
        assertEquals(1026, tick.getInt("x").orElseThrow());

        CompoundTag structures = relocated.getCompoundTag("structures");
        long reference = ((LongArrayTag) structures.getCompoundTag("References").get("village")).getValue()[0];
        assertEquals(64, (int) reference);
        CompoundTag start = structures.getCompoundTag("starts").getCompoundTag("village");
        assertEquals(64, start.getInt("ChunkX").orElseThrow());
        assertArrayEquals(new int[] {1024, 60, 0, 1039, 80, 15}, start.getIntArray("BB").orElseThrow());
        CompoundTag child = (CompoundTag) start.getListTag("Children").get(0);
        assertEquals(1028, child.getInt("TPX").orElseThrow());
        CompoundTag junction = (CompoundTag) child.getListTag("junctions").get(0);
        assertEquals(1030, junction.getInt("source_x").orElseThrow());
    }

    @Test
    void externalChunkReferenceFailsBeforeTargetCellMutation() throws Exception {
        Path source = root.resolve("external/chunks");
        Path worldRegion = root.resolve("worlds/ci_shard_001/region");
        Files.createDirectories(source);
        Files.createDirectories(worldRegion);
        writeExternalChunkHeader(source.resolve("r.0.0.mca"));
        Files.writeString(worldRegion.resolve("r.2.0.mca"), "existing");
        CellPlacementPlan plan = new CellPlacementPlan(
            UUID.fromString("00000000-0000-0000-0000-000000001303"),
            "ci_shard_001", 1024, 0, source, 62, 66, -2, 2, 0, 0, true
        );

        IOException exception = assertThrows(IOException.class, () -> new FileBackedCellTransfer(root.resolve("worlds")).place(plan));

        assertTrue(exception.getMessage().contains(".mcc"));
        assertEquals("existing", Files.readString(worldRegion.resolve("r.2.0.mca")));
    }

    @Test
    void entityAndPoiRegionsRelocateTheirOwnCoordinateSchemas() throws Exception {
        Path entities = root.resolve("source/entities");
        Path poi = root.resolve("source/poi");
        Files.createDirectories(entities);
        Files.createDirectories(poi);

        CompoundTag entityRoot = new CompoundTag();
        entityRoot.put("Position", new IntArrayTag(new int[] {0, 0}));
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:item_frame");
        ListTag<DoubleTag> position = new ListTag<>(DoubleTag.class);
        position.add(new DoubleTag(3.5D));
        position.add(new DoubleTag(70.0D));
        position.add(new DoubleTag(4.5D));
        entity.put("Pos", position);
        entity.putInt("TileX", 3);
        entity.putInt("TileY", 70);
        entity.putInt("TileZ", 4);
        ListTag<CompoundTag> entityList = new ListTag<>(CompoundTag.class);
        entityList.add(entity);
        entityRoot.put("Entities", entityList);
        writeRegion(entities.resolve("r.0.0.mca"), entityRoot);

        CompoundTag poiRoot = new CompoundTag();
        CompoundTag sections = new CompoundTag();
        CompoundTag section = new CompoundTag();
        CompoundTag record = new CompoundTag();
        record.put("pos", new IntArrayTag(new int[] {5, 64, 6}));
        ListTag<CompoundTag> records = new ListTag<>(CompoundTag.class);
        records.add(record);
        section.put("Records", records);
        sections.put("4", section);
        poiRoot.put("Sections", sections);
        writeRegion(poi.resolve("r.0.0.mca"), poiRoot);

        Path relocatedEntities = root.resolve("relocated/entities");
        Path relocatedPoi = root.resolve("relocated/poi");
        AnvilRegionRelocator relocator = new AnvilRegionRelocator();
        relocator.relocate(entities, relocatedEntities, 1024, 0, AnvilRegionRelocator.DataKind.ENTITIES);
        relocator.relocate(poi, relocatedPoi, 1024, 0, AnvilRegionRelocator.DataKind.POI);

        CompoundTag movedEntityRoot = readChunk(relocatedEntities.resolve("r.2.0.mca"), 64, 0);
        assertArrayEquals(new int[] {64, 0}, movedEntityRoot.getIntArray("Position").orElseThrow());
        CompoundTag movedEntity = (CompoundTag) movedEntityRoot.getListTag("Entities").get(0);
        assertEquals(1027.5D, ((DoubleTag) movedEntity.getListTag("Pos").get(0)).asDouble());
        assertEquals(1027, movedEntity.getInt("TileX").orElseThrow());

        CompoundTag movedPoiRoot = readChunk(relocatedPoi.resolve("r.2.0.mca"), 64, 0);
        CompoundTag movedRecord = (CompoundTag) movedPoiRoot.getCompoundTag("Sections").getCompoundTag("4").getListTag("Records").get(0);
        assertArrayEquals(new int[] {1029, 64, 6}, movedRecord.getIntArray("pos").orElseThrow());
    }

    private CompoundTag sampleChunk() {
        CompoundTag rootTag = new CompoundTag();
        rootTag.putInt("DataVersion", 4438);
        rootTag.putInt("xPos", 0);
        rootTag.putInt("zPos", 0);

        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:chest");
        blockEntity.putInt("x", 1);
        blockEntity.putInt("y", 64);
        blockEntity.putInt("z", 2);
        ListTag<CompoundTag> blockEntities = new ListTag<>(CompoundTag.class);
        blockEntities.add(blockEntity);
        rootTag.put("block_entities", blockEntities);

        CompoundTag tick = new CompoundTag();
        tick.putInt("x", 2);
        tick.putInt("y", 63);
        tick.putInt("z", 3);
        ListTag<CompoundTag> ticks = new ListTag<>(CompoundTag.class);
        ticks.add(tick);
        rootTag.put("block_ticks", ticks);

        CompoundTag structures = new CompoundTag();
        CompoundTag references = new CompoundTag();
        references.put("village", new LongArrayTag(new long[] {0L}));
        structures.put("References", references);
        CompoundTag starts = new CompoundTag();
        CompoundTag start = new CompoundTag();
        start.putString("id", "minecraft:village");
        start.putInt("ChunkX", 0);
        start.putInt("ChunkZ", 0);
        start.put("BB", new IntArrayTag(new int[] {0, 60, 0, 15, 80, 15}));
        CompoundTag child = new CompoundTag();
        child.putInt("TPX", 4);
        child.putInt("TPY", 64);
        child.putInt("TPZ", 5);
        child.put("BB", new IntArrayTag(new int[] {1, 60, 1, 8, 70, 8}));
        CompoundTag junction = new CompoundTag();
        junction.putInt("source_x", 6);
        junction.putInt("source_y", 64);
        junction.putInt("source_z", 7);
        ListTag<CompoundTag> junctions = new ListTag<>(CompoundTag.class);
        junctions.add(junction);
        child.put("junctions", junctions);
        ListTag<CompoundTag> children = new ListTag<>(CompoundTag.class);
        children.add(child);
        start.put("Children", children);
        starts.put("village", start);
        structures.put("starts", starts);
        rootTag.put("structures", structures);
        return rootTag;
    }

    private void writeRegion(Path path, CompoundTag rootTag) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(compressed)) {
            new NBTSerializer(false).toStream(new NamedTag("", rootTag), zlib);
        }
        byte[] payload = compressed.toByteArray();
        int sectors = Math.floorDiv(payload.length + 5 + 4095, 4096);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt((2 << 8) | sectors);
            output.write(new byte[4096 - 4]);
            output.writeInt(123456789);
            output.write(new byte[4096 - 4]);
            output.writeInt(payload.length + 1);
            output.writeByte(2);
            output.write(payload);
            output.write(new byte[sectors * 4096 - payload.length - 5]);
        }
    }

    private void writeExternalChunkHeader(Path path) throws Exception {
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt((2 << 8) | 1);
            output.write(new byte[8192 - 4]);
            output.writeInt(2);
            output.writeByte(0x82);
            output.write(new byte[4096 - 5]);
        }
    }

    private CompoundTag readChunk(Path path, int chunkX, int chunkZ) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        int index = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(bytes))) {
            header.skipNBytes(index * 4L);
            int location = header.readInt();
            int offset = (location >>> 8) * 4096;
            int length = (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
            assertEquals(2, bytes[offset + 4]);
            try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes, offset + 5, length - 1))) {
                return (CompoundTag) new NBTDeserializer(false).fromStream(input).getTag();
            }
        }
    }
}
