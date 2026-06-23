package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaperVersionAdapterRegistryTest {
    @Test
    void parsesStablePatchPreReleaseAndServerVersionStrings() {
        assertEquals("1.21.11", ServerVersion.parse("1.21.11").normalized());
        assertEquals("1.21.9-rc1", ServerVersion.parse("1.21.9-rc1").normalized());
        assertEquals("1.21.11", ServerVersion.parse("git-Paper-132 (MC: 1.21.11)").normalized());
        assertEquals("26.1.0", ServerVersion.parse("26.1").normalized());
        assertEquals("26.2.4+build.7", ServerVersion.parse("26.2.4+build.7").normalized());
    }

    @Test
    void selectsRequiredPaperFamilies() {
        PaperVersionAdapterRegistry registry = PaperVersionAdapterRegistry.defaults();

        assertEquals("paper-1.21", registry.select("1.21").adapterId());
        assertEquals("paper-1.21", registry.select("1.21.11").adapterId());
        assertEquals("paper-26.1", registry.select("26.1").adapterId());
        assertEquals("paper-26.2", registry.select("26.2").adapterId());
    }

    @Test
    void rejectsMalformedUnsupportedDuplicateAndIncompleteAdapters() {
        assertThrows(IllegalArgumentException.class, () -> ServerVersion.parse("not-a-version"));
        assertThrows(UnsupportedPaperVersionException.class, () -> PaperVersionAdapterRegistry.defaults().select("27.0"));
        assertThrows(IllegalArgumentException.class, () -> new PaperVersionAdapterRegistry(List.of(
            new DefaultPaperVersionAdapter("paper-1.21-a", VersionRange.majorMinor("paper-1.21", 1, 21), RuntimeCapabilities.baseline()),
            new DefaultPaperVersionAdapter("paper-1.21-b", VersionRange.majorMinor("paper-1.21-copy", 1, 21), RuntimeCapabilities.baseline())
        )));
        PaperVersionAdapterRegistry incomplete = new PaperVersionAdapterRegistry(List.of(
            new DefaultPaperVersionAdapter("paper-1.21", VersionRange.majorMinor("paper-1.21", 1, 21), new RuntimeCapabilities(true, false, true, true, true, true))
        ));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> incomplete.select("1.21.11"));
        assertTrue(failure.getMessage().contains("adapter=paper-1.21"));
    }

    @Test
    void unsupportedErrorsIncludeOriginalNormalizedAndSupportedRange() {
        UnsupportedPaperVersionException failure = assertThrows(
            UnsupportedPaperVersionException.class,
            () -> PaperVersionAdapterRegistry.defaults().select("git-Paper-test (MC: 1.20.6)")
        );

        assertTrue(failure.getMessage().contains("original=git-Paper-test (MC: 1.20.6)"));
        assertTrue(failure.getMessage().contains("normalized=1.20.6"));
        assertTrue(failure.getMessage().contains("paper-1.21"));
        assertTrue(failure.getMessage().contains("paper-26.1"));
        assertTrue(failure.getMessage().contains("paper-26.2"));
    }
}
