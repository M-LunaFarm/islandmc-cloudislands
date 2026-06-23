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
        assertTrue(diagnostics.contains("paperAdapterSelfTest=passed=true"));
    }

    @Test
    void oneTwentyOneFamilySelfTestFailsFastOnRequiredCapabilityGaps() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> PaperRuntimeCompatibility.select(
                "1.21.4",
                "",
                PaperVersionAdapterRegistry.defaults(),
                capabilities(false, true, true, true, true)
            )
        );

        assertTrue(failure.getMessage().contains("Paper adapter startup self-test failed"));
        assertTrue(failure.getMessage().contains("adapter=paper-1.21"));
        assertTrue(failure.getMessage().contains("paper-minor-api-version"));
    }

    @Test
    void oneTwentyOneFamilySelfTestKeepsOptionalCapabilityDiagnostics() {
        PaperRuntimeCompatibility.RuntimeSelection selection = PaperRuntimeCompatibility.select(
            "1.21.11",
            "",
            PaperVersionAdapterRegistry.defaults(),
            capabilities(true, false, false, false, false)
        );

        String diagnostics = selection.diagnosticsSection();

        assertEquals("paper-1.21", selection.adapterId());
        assertTrue(selection.selfTest().passed());
        assertTrue(diagnostics.contains("paperAdapterSelfTest=passed=true"));
        assertTrue(diagnostics.contains("optionalWarnings=region-scheduler|data-components|dialog-api|registry-mutation"));
    }

    private static PaperCapabilities capabilities(
        boolean minorApi,
        boolean regionScheduler,
        boolean dataComponents,
        boolean dialogApi,
        boolean registryMutation
    ) {
        return new PaperCapabilities() {
            @Override
            public boolean supportsRegionScheduler() {
                return regionScheduler;
            }

            @Override
            public boolean supportsDataComponents() {
                return dataComponents;
            }

            @Override
            public boolean supportsMinorApiVersion() {
                return minorApi;
            }

            @Override
            public boolean supportsDialogApi() {
                return dialogApi;
            }

            @Override
            public boolean supportsRegistryMutation() {
                return registryMutation;
            }
        };
    }
}
