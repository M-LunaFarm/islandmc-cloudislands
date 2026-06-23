package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperRuntimeCompatibilityTest {
    @Test
    void selectsAdapterFromMinecraftVersionBeforeServerVersionString() {
        PaperRuntimeCompatibility.RuntimeSelection selection = PaperRuntimeCompatibility.select(
            "1.21.11",
            "git-Paper-test (MC: 27.0)",
            PaperVersionAdapterRegistry.defaults()
        );

        assertEquals("paper-1.21", selection.adapterId());
        assertEquals("1.21.11", selection.version().normalized());
    }

    @Test
    void fallsBackToServerVersionWhenMinecraftVersionIsBlank() {
        PaperRuntimeCompatibility.RuntimeSelection selection = PaperRuntimeCompatibility.select(
            "",
            "git-Paper-test (MC: 26.2.4+build.7)",
            PaperVersionAdapterRegistry.defaults()
        );

        assertEquals("paper-26.2", selection.adapterId());
        assertEquals("26.2.4+build.7", selection.version().normalized());
    }

    @Test
    void unsupportedRuntimeFailsBeforeAdapterSelectionCanContinue() {
        UnsupportedPaperVersionException failure = assertThrows(
            UnsupportedPaperVersionException.class,
            () -> PaperRuntimeCompatibility.select("27.0", "git-Paper-test (MC: 27.0)", PaperVersionAdapterRegistry.defaults())
        );

        assertTrue(failure.getMessage().contains("original=27.0"));
        assertTrue(failure.getMessage().contains("normalized=27.0.0"));
        assertTrue(failure.getMessage().contains("paper-1.21"));
        assertTrue(failure.getMessage().contains("paper-26.1"));
        assertTrue(failure.getMessage().contains("paper-26.2"));
    }

    @Test
    void diagnosticsExposeAdapterVersionRangeAndCapabilities() {
        PaperRuntimeCompatibility.RuntimeSelection selection = PaperRuntimeCompatibility.select(
            "26.1",
            "",
            PaperVersionAdapterRegistry.defaults()
        );

        String diagnostics = selection.diagnosticsSection();

        assertTrue(diagnostics.contains("## runtime-compatibility"));
        assertTrue(diagnostics.contains("paperVersionOriginal=26.1"));
        assertTrue(diagnostics.contains("paperVersionNormalized=26.1.0"));
        assertTrue(diagnostics.contains("paperAdapterId=paper-26.1"));
        assertTrue(diagnostics.contains("paperAdapterRange=paper-26.1=26.1.x"));
        assertTrue(diagnostics.contains("scheduler=true"));
        assertTrue(diagnostics.contains("bundleRestore=true"));
    }
}
