package kr.lunaf.cloudislands.paper.world.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalTarBundleExtractorTest {
    @TempDir
    Path root;

    @Test
    void archiveValidationAcceptsRequiredRegularTree() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();

        assertDoesNotThrow(() -> extractor.validateArchiveEntries(bundleFile(4096), safeEntries()));
    }

    @Test
    void archiveValidationRejectsTraversalAndSpecialEntryTypesBeforeExtraction() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();
        Path bundle = bundleFile(4096);

        List<ExternalTarBundleExtractor.ArchiveEntry> traversal = safeEntries();
        traversal.add(new ExternalTarBundleExtractor.ArchiveEntry("../world/region.mca", '-', 1L));
        assertThrows(IOException.class, () -> extractor.validateArchiveEntries(bundle, traversal));

        for (char type : List.of('l', 'h', 'p', 'c', 'b', 's', 'S')) {
            List<ExternalTarBundleExtractor.ArchiveEntry> entries = safeEntries();
            entries.add(new ExternalTarBundleExtractor.ArchiveEntry("chunks/unsafe-" + type, type, 0L));
            assertThrows(IOException.class, () -> extractor.validateArchiveEntries(bundle, entries), "type " + type);
        }
    }

    @Test
    void archiveValidationRejectsCompressionRatioBomb() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();
        List<ExternalTarBundleExtractor.ArchiveEntry> entries = safeEntries();
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("chunks/oversized.mca", '-', 4096L));

        assertThrows(IOException.class, () -> extractor.validateArchiveEntries(bundleFile(1), entries));
    }

    @Test
    void archiveValidationRejectsOverlongEntryPathsBeforeExtraction() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();
        List<ExternalTarBundleExtractor.ArchiveEntry> entries = safeEntries();
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("chunks/" + "a".repeat(4097) + ".mca", '-', 1L));

        IOException exception = assertThrows(IOException.class, () -> extractor.validateArchiveEntries(bundleFile(4096), entries));

        assertTrue(exception.getMessage().contains("path exceeds"));
    }

    @Test
    void failedPreExtractionValidationKeepsExistingTargetDirectory() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor(Duration.ofSeconds(2));
        Path invalidBundle = bundleFile(128);
        Path target = root.resolve("target-world");
        Files.createDirectories(target);
        Files.writeString(target.resolve("marker.txt"), "existing-world");

        assertThrows(IOException.class, () -> extractor.extract(invalidBundle, target));

        assertEquals("existing-world", Files.readString(target.resolve("marker.txt")));
    }

    @Test
    void checksumVerificationRejectsDuplicateChecksumEntries() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor(Duration.ofSeconds(5));
        Path source = root.resolve("source");
        Path chunks = source.resolve("chunks");
        Files.createDirectories(chunks);
        Files.createDirectories(source.resolve("entities"));
        Files.createDirectories(source.resolve("block-entities"));
        Path manifest = source.resolve("manifest.json");
        Path chunk = chunks.resolve("r.0.0.mca");
        Files.writeString(manifest, "{}", StandardCharsets.UTF_8);
        Files.writeString(chunk, "chunk-data", StandardCharsets.UTF_8);
        Path checksums = source.resolve("checksums.sha256");
        Files.writeString(
            checksums,
            sha256(manifest) + "  manifest.json\n"
                + sha256(chunk) + "  chunks/r.0.0.mca\n"
                + sha256(chunk) + "  chunks/r.0.0.mca\n",
            StandardCharsets.UTF_8
        );

        IOException exception = assertThrows(IOException.class, () -> extractor.verifyChecksums(source, checksums));

        assertTrue(exception.getMessage().contains("duplicate checksum entry"));
    }

    @Test
    void extractedTreeValidationRejectsSymlinks() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();

        Path symlinkRoot = root.resolve("symlink-tree");
        Files.createDirectories(symlinkRoot.resolve("chunks"));
        Files.createSymbolicLink(symlinkRoot.resolve("chunks/link"), Path.of("../outside"));
        IOException symlink = assertThrows(IOException.class, () -> extractor.validateExtractedTree(symlinkRoot));
        assertTrue(symlink.getMessage().contains("symbolic links are not allowed"));
    }

    @Test
    void extractedTreeValidationRejectsHardlinksWhenFilesystemReportsLinkCounts() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();

        Path hardlinkRoot = root.resolve("hardlink-tree");
        Files.createDirectories(hardlinkRoot.resolve("chunks"));
        Path original = hardlinkRoot.resolve("chunks/original.mca");
        Path hardlink = hardlinkRoot.resolve("chunks/hardlink.mca");
        Files.writeString(original, "chunk", StandardCharsets.UTF_8);
        try {
            Files.createLink(hardlink, original);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "hard links are not supported by this filesystem: " + exception.getMessage());
        }
        IOException hardlinkFailure = assertThrows(IOException.class, () -> extractor.validateExtractedTree(hardlinkRoot));
        assertTrue(hardlinkFailure.getMessage().contains("hard links are not allowed"));
    }

    @Test
    void extractedTreeValidationAcceptsRegularFilesWhenSparseBlockMetadataIsUnavailable() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();
        Path regularRoot = root.resolve("regular-tree");
        Files.createDirectories(regularRoot.resolve("chunks"));
        Files.writeString(regularRoot.resolve("chunks/r.0.0.mca"), "chunk", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> extractor.validateExtractedTree(regularRoot));
    }

    @Test
    void verboseTarListingParserKeepsEntryTypeAndName() throws Exception {
        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();

        ExternalTarBundleExtractor.ArchiveEntry symlink = extractor.parseArchiveEntry("lrwxrwxrwx root/root 0 2026-06-23 00:00 ./chunks/link -> ../outside");
        ExternalTarBundleExtractor.ArchiveEntry file = extractor.parseArchiveEntry("-rw-r--r-- root/root 123 2026-06-23 00:00 ./chunks/r.0.0.mca");

        assertEquals('l', symlink.type());
        assertEquals("chunks/link", symlink.name());
        assertEquals('-', file.type());
        assertEquals("chunks/r.0.0.mca", file.name());
        assertEquals(123L, file.sizeBytes());
    }

    @Test
    void tarProcessTimesOutAndBoundsOutput() {
        ExternalTarBundleExtractor timeoutExtractor = new ExternalTarBundleExtractor(Duration.ofMillis(100));
        IOException timeout = assertThrows(IOException.class, () -> timeoutExtractor.runTar(List.of("sh", "-c", "sleep 2"), "test timeout"));
        assertTrue(timeout.getMessage().contains("timed out"));

        ExternalTarBundleExtractor extractor = new ExternalTarBundleExtractor();
        IOException output = assertThrows(IOException.class, () -> extractor.runTar(List.of("sh", "-c", "yes x | head -c 70000"), "test output"));
        assertTrue(output.getMessage().contains("tar output exceeded"));
    }

    @Test
    void extractorSyncsValidatedStagingTreeBeforePublishing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/world/bundle/ExternalTarBundleExtractor.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("syncExtractedTree(staging);"));
        assertTrue(source.contains("Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);"));
        assertTrue(source.contains("fsyncDirectory(parent);"));
    }

    private Path bundleFile(int bytes) throws IOException {
        Path bundle = root.resolve("bundle-" + bytes + ".tar.zst");
        byte[] content = new byte[Math.max(1, bytes)];
        Files.write(bundle, content);
        return bundle;
    }

    private List<ExternalTarBundleExtractor.ArchiveEntry> safeEntries() {
        List<ExternalTarBundleExtractor.ArchiveEntry> entries = new ArrayList<>();
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("manifest.json", '-', 2L));
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("checksums.sha256", '-', 128L));
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("chunks", 'd', 0L));
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("chunks/r.0.0.mca", '-', 512L));
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("entities", 'd', 0L));
        entries.add(new ExternalTarBundleExtractor.ArchiveEntry("block-entities", 'd', 0L));
        return entries;
    }

    private String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
