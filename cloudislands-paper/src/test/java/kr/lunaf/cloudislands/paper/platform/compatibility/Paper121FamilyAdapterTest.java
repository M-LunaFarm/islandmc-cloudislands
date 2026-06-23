package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Paper121FamilyAdapterTest {
    @Test
    void supportsTheEntirePaperOneTwentyOneFamily() {
        Paper121FamilyAdapter adapter = new Paper121FamilyAdapter();

        assertEquals("paper-1.21", adapter.adapterId());
        assertTrue(adapter.supports(ServerVersion.parse("1.21")));
        assertTrue(adapter.supports(ServerVersion.parse("1.21.0")));
        assertTrue(adapter.supports(ServerVersion.parse("1.21.4")));
        assertTrue(adapter.supports(ServerVersion.parse("1.21.11")));
        assertTrue(adapter.supports(ServerVersion.parse("1.21.99+build.42")));
        assertFalse(adapter.supports(ServerVersion.parse("1.20.6")));
        assertFalse(adapter.supports(ServerVersion.parse("1.22.0")));
    }

    @Test
    void treatsPatchSpecificApisAsOptionalDiagnosticsOnly() {
        PaperAdapterSelfTest selfTest = new Paper121FamilyAdapter().startupSelfTest(capabilities(
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
    void failsOnlyWhenRequiredFamilyContractIsMissing() {
        PaperAdapterSelfTest selfTest = new Paper121FamilyAdapter().startupSelfTest(capabilities(
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
