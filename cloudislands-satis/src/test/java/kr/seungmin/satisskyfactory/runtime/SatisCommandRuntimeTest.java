package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisCommandRuntimeTest {
    @Test
    void commandRegistrationResultReportsMissingCommandsAsInactive() {
        SatisCommandRuntime.CommandRegistrationResult result =
                SatisCommandRuntime.CommandRegistrationResult.missing("factory");

        assertFalse(result.present());
        assertFalse(result.registered());
    }

    @Test
    void commandRegistrationResultSeparatesPresenceFromRegistrationState() {
        SatisCommandRuntime.CommandRegistrationResult result =
                SatisCommandRuntime.CommandRegistrationResult.present("sfactory", false);

        assertTrue(result.present());
        assertFalse(result.registered());
    }
}
