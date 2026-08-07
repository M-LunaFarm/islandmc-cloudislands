package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeBootstrapTest {
    private final SatisRuntimeBootstrap bootstrap = new SatisRuntimeBootstrap();

    @Test
    void bootstrapStartsRuntimeWhenAddonRegistrationSucceeds() {
        SatisRuntimeBootstrap.RuntimeBootstrapDecision decision = bootstrap.decide(
                new SatisRuntimeBootstrap.RuntimeBootstrapSnapshot(true, false));

        assertTrue(decision.startRuntime());
        assertFalse(decision.unregisterCommands());
        assertFalse(decision.disablePlugin());
    }

    @Test
    void bootstrapWaitsWithoutDisablingWhenApiIsTemporarilyMissing() {
        SatisRuntimeBootstrap.RuntimeBootstrapDecision decision = bootstrap.decide(
                new SatisRuntimeBootstrap.RuntimeBootstrapSnapshot(false, true));

        assertFalse(decision.startRuntime());
        assertTrue(decision.unregisterCommands());
        assertFalse(decision.disablePlugin());
    }
}
