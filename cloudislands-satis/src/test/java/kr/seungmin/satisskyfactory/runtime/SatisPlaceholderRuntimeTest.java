package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisPlaceholderRuntimeTest {
    @Test
    void placeholderRuntimeGateRequiresFeatureMachinesAndPlaceholderApi() {
        assertEquals("placeholders-feature-disabled", SatisPlaceholderRuntime.gate(false, true, true).reason());
        assertEquals("machines-feature-disabled", SatisPlaceholderRuntime.gate(true, false, true).reason());
        assertEquals("placeholderapi-not-installed", SatisPlaceholderRuntime.gate(true, true, false).reason());
        assertFalse(SatisPlaceholderRuntime.gate(true, false, true).enabled());
        assertTrue(SatisPlaceholderRuntime.gate(true, true, true).enabled());
        assertEquals("none", SatisPlaceholderRuntime.gate(true, true, true).reason());
    }
}
