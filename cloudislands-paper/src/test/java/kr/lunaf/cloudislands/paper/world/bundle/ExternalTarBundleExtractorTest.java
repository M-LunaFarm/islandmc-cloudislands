package kr.lunaf.cloudislands.paper.world.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
