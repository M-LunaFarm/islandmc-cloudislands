package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SatisListenerRuntimeTest {
    @Test
    void listenerStateSeparatesMissingUnregisteredAndRegisteredComponents() {
        assertEquals(SatisListenerRuntime.RegistrationState.MISSING, SatisListenerRuntime.state(false, false));
        assertEquals(SatisListenerRuntime.RegistrationState.UNREGISTERED, SatisListenerRuntime.state(true, false));
        assertEquals(SatisListenerRuntime.RegistrationState.REGISTERED, SatisListenerRuntime.state(true, true));
    }
}
