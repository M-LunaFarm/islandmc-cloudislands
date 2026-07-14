package kr.lunaf.cloudislands.paper.world.cell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.Tag;

/** Relocates modern Java Edition region chunks without loading a Bukkit world. */
final class AnvilRegionRelocator {
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = SECTOR_BYTES * 2;
    private static final int MAX_DECOMPRESSED_CHUNK_BYTES = 64 * 1024 * 1024;

    void relocate(Path source, Path target, int blockOffsetX, int blockOffsetZ) throws IOException {
        if (blockOffsetX % 16 != 0 || blockOffsetZ % 16 != 0) {
            throw new IOException("cell relocation offset must be chunk aligned");
        }
        Files.createDirectories(target);
        Map<RegionCoordinate, RegionChunks> relocated = new HashMap<>();
        if (!Files.exists(source)) {
            return;
        }
        try (var paths = Files.list(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island region bundles: " + path);
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                RegionCoordinate fileCoordinate = RegionCoordinate.parse(path.getFileName().toString());
                if (fileCoordinate != null) {
                    readRegion(path, fileCoordinate, blockOffsetX, blockOffsetZ, relocated);
                }
            }
        }
        for (Map.Entry<RegionCoordinate, RegionChunks> entry : relocated.entrySet()) {
            writeRegion(target.resolve(entry.getKey().fileName()), entry.getValue());
        }
    }

    private void readRegion(Path path, RegionCoordinate fileCoordinate, int blockOffsetX, int blockOffsetZ,
                            Map<RegionCoordinate, RegionChunks> relocated) throws IOException {
        long fileSize = Files.size(path);
        if (fileSize < HEADER_BYTES || fileSize % SECTOR_BYTES != 0) {
            throw new IOException("invalid Anvil region file size: " + path.getFileName());
        }
        long sectorTotal = fileSize / SECTOR_BYTES;
        if (sectorTotal > 0x10000ffL) {
            throw new IOException("Anvil region file exceeds addressable sector range: " + path.getFileName());
        }
        BitSet occupied = new BitSet((int) sectorTotal);
        occupied.set(0, 2);
        byte[] headerBytes = new byte[HEADER_BYTES];
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            readFully(channel, ByteBuffer.wrap(headerBytes), 0, path);
            DataInputStream header = new DataInputStream(new ByteArrayInputStream(headerBytes));
            int[] locations = new int[1024];
            for (int index = 0; index < locations.length; index++) {
                locations[index] = header.readInt();
            }
            int[] timestamps = new int[1024];
            for (int index = 0; index < timestamps.length; index++) {
                timestamps[index] = header.readInt();
            }
            for (int index = 0; index < locations.length; index++) {
                int location = locations[index];
                int sectorOffset = location >>> 8;
                int sectorCount = location & 0xff;
                if (sectorOffset == 0 && sectorCount == 0) {
                    continue;
                }
                if (sectorOffset < 2 || sectorCount == 0 || sectorOffset + sectorCount > sectorTotal) {
                    throw new IOException("invalid Anvil chunk location in " + path.getFileName() + " slot " + index);
                }
                if (occupied.nextSetBit(sectorOffset) >= 0 && occupied.nextSetBit(sectorOffset) < sectorOffset + sectorCount) {
                    throw new IOException("overlapping Anvil chunk sectors in " + path.getFileName());
                }
                occupied.set(sectorOffset, sectorOffset + sectorCount);
                long byteOffset = (long) sectorOffset * SECTOR_BYTES;
                ByteBuffer chunkHeader = ByteBuffer.allocate(5);
                readFully(channel, chunkHeader, byteOffset, path);
                byte[] chunkHeaderBytes = chunkHeader.array();
                int length = readInt(chunkHeaderBytes, 0);
                if (length < 2 || length > sectorCount * SECTOR_BYTES - 4 || byteOffset + 4L + length > fileSize) {
                    throw new IOException("invalid Anvil chunk length in " + path.getFileName() + " slot " + index);
                }
                int compression = chunkHeaderBytes[4] & 0xff;
                if ((compression & 0x80) != 0) {
                    throw new IOException("external Anvil .mcc chunks are not supported by portable bundles: " + path.getFileName());
                }
                byte[] payload = new byte[length - 1];
                readFully(channel, ByteBuffer.wrap(payload), byteOffset + 5, path);
                CompoundTag root = deserialize(payload, 0, payload.length, compression, path);
                int expectedX = fileCoordinate.regionX * 32 + index % 32;
                int expectedZ = fileCoordinate.regionZ * 32 + index / 32;
                int sourceX = requiredInt(root, "xPos", path);
                int sourceZ = requiredInt(root, "zPos", path);
                if (sourceX != expectedX || sourceZ != expectedZ) {
                    throw new IOException("chunk coordinates do not match Anvil header slot in " + path.getFileName());
                }
                relocateNbt(root, blockOffsetX, blockOffsetZ);
                int targetX = Math.addExact(sourceX, blockOffsetX / 16);
                int targetZ = Math.addExact(sourceZ, blockOffsetZ / 16);
                RegionCoordinate targetRegion = new RegionCoordinate(Math.floorDiv(targetX, 32), Math.floorDiv(targetZ, 32));
                int targetIndex = Math.floorMod(targetX, 32) + Math.floorMod(targetZ, 32) * 32;
                RegionChunks chunks = relocated.computeIfAbsent(targetRegion, ignored -> new RegionChunks());
                if (chunks.chunks[targetIndex] != null) {
                    throw new IOException("relocation produced duplicate target chunk " + targetX + "," + targetZ);
                }
                chunks.chunks[targetIndex] = new RelocatedChunk(root, timestamps[index]);
            }
        } catch (ArithmeticException exception) {
            throw new IOException("cell relocation coordinate overflow", exception);
        }
    }

    private void readFully(FileChannel channel, ByteBuffer buffer, long position, Path path) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) {
                throw new IOException("truncated Anvil region file: " + path.getFileName());
            }
        }
    }

    private CompoundTag deserialize(byte[] bytes, int offset, int length, int compression, Path path) throws IOException {
        Supplier<InputStream> compressed = () -> new ByteArrayInputStream(bytes, offset, length);
        InputStream decoded = switch (compression) {
            case 1 -> new GZIPInputStream(compressed.get());
            case 2 -> new InflaterInputStream(compressed.get());
            case 3 -> compressed.get();
            case 4 -> new LZ4BlockInputStream(compressed.get());
            default -> throw new IOException("unsupported Anvil compression type " + compression + " in " + path.getFileName());
        };
        try (InputStream limited = new SizeLimitedInputStream(decoded, MAX_DECOMPRESSED_CHUNK_BYTES)) {
            NamedTag named = new NBTDeserializer(false).fromStream(limited);
            if (!(named.getTag() instanceof CompoundTag root)) {
                throw new IOException("Anvil chunk root is not a compound in " + path.getFileName());
            }
            return root;
        } catch (RuntimeException exception) {
            throw new IOException("invalid Anvil NBT in " + path.getFileName(), exception);
        }
    }

    private void writeRegion(Path target, RegionChunks region) throws IOException {
        byte[][] payloads = new byte[1024][];
        int[] sectorCounts = new int[1024];
        int sectors = 2;
        for (int index = 0; index < region.chunks.length; index++) {
            RelocatedChunk chunk = region.chunks[index];
            if (chunk == null) {
                continue;
            }
            ByteArrayOutputStream nbt = new ByteArrayOutputStream();
            try (OutputStream compressed = new DeflaterOutputStream(nbt)) {
                new NBTSerializer(false).toStream(new NamedTag("", chunk.root), compressed);
            }
            payloads[index] = nbt.toByteArray();
            int sectorCount = Math.floorDiv(payloads[index].length + 5 + SECTOR_BYTES - 1, SECTOR_BYTES);
            if (sectorCount > 255) {
                throw new IOException("relocated Anvil chunk exceeds internal sector limit");
            }
            sectorCounts[index] = sectorCount;
            sectors = Math.addExact(sectors, sectorCount);
        }
        Files.createDirectories(target.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            int sectorOffset = 2;
            for (int index = 0; index < 1024; index++) {
                if (payloads[index] == null) {
                    output.writeInt(0);
                } else {
                    output.writeInt((sectorOffset << 8) | sectorCounts[index]);
                    sectorOffset += sectorCounts[index];
                }
            }
            for (RelocatedChunk chunk : region.chunks) {
                output.writeInt(chunk == null ? 0 : chunk.timestamp);
            }
            for (int index = 0; index < 1024; index++) {
                byte[] payload = payloads[index];
                if (payload == null) {
                    continue;
                }
                output.writeInt(payload.length + 1);
                output.writeByte(2);
                output.write(payload);
                output.write(new byte[sectorCounts[index] * SECTOR_BYTES - payload.length - 5]);
            }
        } catch (ArithmeticException exception) {
            throw new IOException("relocated Anvil region is too large", exception);
        }
    }

    private void relocateNbt(CompoundTag root, int offsetX, int offsetZ) throws IOException {
        int chunkOffsetX = offsetX / 16;
        int chunkOffsetZ = offsetZ / 16;
        root.putInt("xPos", Math.addExact(requiredInt(root, "xPos", null), chunkOffsetX));
        root.putInt("zPos", Math.addExact(requiredInt(root, "zPos", null), chunkOffsetZ));
        relocatePositionList(root.getListTag("block_entities"), offsetX, offsetZ);
        relocatePositionList(root.getListTag("block_ticks"), offsetX, offsetZ);
        relocatePositionList(root.getListTag("fluid_ticks"), offsetX, offsetZ);
        relocateStructures(root.getCompoundTag("structures"), offsetX, offsetZ, chunkOffsetX, chunkOffsetZ);
    }

    private void relocatePositionList(ListTag<?> list, int offsetX, int offsetZ) {
        if (list == null) return;
        for (Tag<?> tag : list) {
            if (tag instanceof CompoundTag compound) {
                addIntPair(compound, "x", "z", offsetX, offsetZ);
                CompoundTag exitPortal = compound.getCompoundTag("ExitPortal");
                addIntPair(exitPortal, "X", "Z", offsetX, offsetZ);
                addIntPair(compound, "posX", "posZ", offsetX, offsetZ);
                relocateSpawnerEntities(compound, offsetX, offsetZ);
            }
        }
    }

    private void relocateSpawnerEntities(CompoundTag blockEntity, int offsetX, int offsetZ) {
        ListTag<?> potentials = blockEntity.getListTag("SpawnPotentials");
        if (potentials == null) return;
        for (Tag<?> tag : potentials) {
            if (tag instanceof CompoundTag potential) {
                CompoundTag entity = potential.getCompoundTag("Entity");
                if (entity == null) entity = potential.getCompoundTag("data");
                relocateEntityPosition(entity, offsetX, offsetZ);
            }
        }
    }

    private void relocateEntityPosition(CompoundTag entity, int offsetX, int offsetZ) {
        if (entity == null) return;
        ListTag<?> pos = entity.getListTag("Pos");
        if (pos != null && pos.size() >= 3 && pos.get(0) instanceof net.querz.nbt.tag.DoubleTag x
            && pos.get(2) instanceof net.querz.nbt.tag.DoubleTag z) {
            x.setValue(x.asDouble() + offsetX);
            z.setValue(z.asDouble() + offsetZ);
        }
    }

    private void relocateStructures(CompoundTag structures, int offsetX, int offsetZ, int chunkOffsetX, int chunkOffsetZ) {
        if (structures == null) return;
        CompoundTag references = structures.getCompoundTag("References");
        if (references != null) {
            for (Map.Entry<String, Tag<?>> entry : references) {
                if (entry.getValue() instanceof LongArrayTag array) {
                    long[] values = array.getValue();
                    for (int i = 0; i < values.length; i++) {
                        int x = (int) values[i];
                        int z = (int) (values[i] >>> 32);
                        values[i] = ((long) (z + chunkOffsetZ) << 32) | (x + chunkOffsetX & 0xffffffffL);
                    }
                }
            }
        }
        CompoundTag starts = structures.getCompoundTag("starts");
        if (starts == null) return;
        for (Map.Entry<String, Tag<?>> entry : starts) {
            if (!(entry.getValue() instanceof CompoundTag start)) continue;
            addIntPair(start, "ChunkX", "ChunkZ", chunkOffsetX, chunkOffsetZ);
            relocateBoundingBox(start.getIntArrayTag("BB"), offsetX, offsetZ);
            relocateProcessed(start.getListTag("Processed"), chunkOffsetX, chunkOffsetZ);
            ListTag<?> children = start.getListTag("Children");
            if (children == null) continue;
            for (Tag<?> childTag : children) {
                if (!(childTag instanceof CompoundTag child)) continue;
                addIntPair(child, "TPX", "TPZ", offsetX, offsetZ);
                addIntPair(child, "PosX", "PosZ", offsetX, offsetZ);
                relocateBoundingBox(child.getIntArrayTag("BB"), offsetX, offsetZ);
                ListTag<?> entrances = child.getListTag("Entrances");
                if (entrances != null) {
                    for (Tag<?> entrance : entrances) {
                        if (entrance instanceof IntArrayTag bb) relocateBoundingBox(bb, offsetX, offsetZ);
                    }
                }
                ListTag<?> junctions = child.getListTag("junctions");
                if (junctions != null) {
                    for (Tag<?> junction : junctions) {
                        if (junction instanceof CompoundTag compound) addIntPair(compound, "source_x", "source_z", offsetX, offsetZ);
                    }
                }
            }
        }
    }

    private void relocateProcessed(ListTag<?> processed, int offsetX, int offsetZ) {
        if (processed == null) return;
        for (Tag<?> tag : processed) {
            if (tag instanceof CompoundTag compound) addIntPair(compound, "X", "Z", offsetX, offsetZ);
        }
    }

    private void relocateBoundingBox(IntArrayTag tag, int offsetX, int offsetZ) {
        if (tag == null) return;
        int[] values = tag.getValue();
        if (values.length == 6) {
            values[0] += offsetX;
            values[2] += offsetZ;
            values[3] += offsetX;
            values[5] += offsetZ;
        }
    }

    private void addIntPair(CompoundTag tag, String xKey, String zKey, int offsetX, int offsetZ) {
        if (tag == null || tag.getInt(xKey).isEmpty() || tag.getInt(zKey).isEmpty()) return;
        tag.putInt(xKey, Math.addExact(tag.getInt(xKey).orElseThrow(), offsetX));
        tag.putInt(zKey, Math.addExact(tag.getInt(zKey).orElseThrow(), offsetZ));
    }

    private int requiredInt(CompoundTag tag, String key, Path path) throws IOException {
        return tag.getInt(key).orElseThrow(() -> new IOException("Anvil chunk is missing " + key + (path == null ? "" : " in " + path.getFileName())));
    }

    private int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16 | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
    }

    private record RelocatedChunk(CompoundTag root, int timestamp) {}

    private static final class RegionChunks {
        private final RelocatedChunk[] chunks = new RelocatedChunk[1024];
    }

    private record RegionCoordinate(int regionX, int regionZ) {
        private static RegionCoordinate parse(String fileName) {
            if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) return null;
            String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
            if (parts.length != 2) return null;
            try {
                return new RegionCoordinate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String fileName() {
            return "r." + regionX + "." + regionZ + ".mca";
        }
    }

    private static final class SizeLimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private SizeLimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) throw new IOException("decompressed Anvil chunk exceeds safety limit");
            int value = delegate.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) throw new IOException("decompressed Anvil chunk exceeds safety limit");
            int read = delegate.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
