package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Paper261AdapterTest {
    @Test
    void supportsOnlyThePaperTwentySixOneFamily() {
        Paper261Adapter adapter = new Paper261Adapter();

        assertEquals("paper-26.1", adapter.adapterId());
        assertTrue(adapter.supports(ServerVersion.parse("26.1")));
        assertTrue(adapter.supports(ServerVersion.parse("26.1.0")));
        assertTrue(adapter.supports(ServerVersion.parse("26.1.4")));
        assertTrue(adapter.supports(ServerVersion.parse("26.1.99+build.7")));
        assertFalse(adapter.supports(ServerVersion.parse("26.0.9")));
        assertFalse(adapter.supports(ServerVersion.parse("26.2.0")));
        assertFalse(adapter.supports(ServerVersion.parse("27.0.0")));
    }

    @Test
    void reportsPatchSpecificApiGapsAsOptionalDiagnostics() {
        PaperAdapterSelfTest selfTest = new Paper261Adapter().startupSelfTest(capabilities(
            true,
            false,
            false,
            false,
            false
        ));

        assertTrue(selfTest.passed());
        assertEquals(
            "passed=true,requiredFailures=,optionalWarnings=region-scheduler|data-components|dialog-api|registry-mutation",
            selfTest.summary()
        );
    }

    @Test
    void failsStartupWhenTheRequiredFamilyContractIsMissing() {
        PaperAdapterSelfTest selfTest = new Paper261Adapter().startupSelfTest(capabilities(
            false,
            true,
            true,
            true,
            true
        ));

        assertFalse(selfTest.passed());
        assertEquals("paper-minor-api-version", String.join(",", selfTest.requiredFailures()));
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
