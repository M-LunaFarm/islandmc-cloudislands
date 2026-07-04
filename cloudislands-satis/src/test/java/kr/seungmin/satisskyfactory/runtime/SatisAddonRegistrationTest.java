package kr.seungmin.satisskyfactory.runtime;

import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisAddonRegistrationTest {
    @Test
    void missingApiStateFailsClosedWithoutFeatures() {
        SatisAddonRegistration.RuntimeState state = SatisAddonRegistration.RuntimeState.disabled(true);

        assertFalse(state.runtimeEnabled());
        assertTrue(state.cloudIslandsApiMissing());
        assertTrue(state.features().isEmpty());
        assertFalse(state.addonStateReportingEnabled());
        assertEquals("cloudislands-api-missing", state.blockReason());
    }

    @Test
    void registeredStateCarriesFeatureSnapshotWhenActivationAllowsRuntime() {
        SatisAddonRegistration.RuntimeState state = SatisAddonRegistration.registeredState(
                snapshot(true, Map.of("commands", true, "machines", false)),
                "external-plugin",
                true,
                true
        );

        assertTrue(state.runtimeEnabled());
        assertFalse(state.cloudIslandsApiMissing());
        assertEquals(Boolean.TRUE, state.features().get("commands"));
        assertTrue(state.addonStateReportingEnabled());
        assertEquals("none", state.blockReason());
    }

    private CloudIslandsAddonSnapshot snapshot(boolean enabled, Map<String, Boolean> features) {
        return new CloudIslandsAddonSnapshot(
                "cloudislands-satis",
                "CloudIslands Satis",
                "1.0.0",
                enabled,
                Instant.EPOCH,
                Instant.EPOCH,
                features,
                features,
                Map.of()
        );
    }
}
