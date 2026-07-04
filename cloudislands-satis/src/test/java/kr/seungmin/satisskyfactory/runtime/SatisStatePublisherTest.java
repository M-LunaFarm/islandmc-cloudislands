package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SatisStatePublisherTest {
    @Test
    void startupHydrationStateNormalizesReasonCountBackendAndTimestamp() {
        Map<String, String> state = SatisStatePublisher.startupHydrationState(
                "",
                -3,
                "",
                "target-tick-start",
                "cloudislands-api",
                Instant.parse("2026-07-04T00:00:00Z")
        );

        assertEquals("startup", state.get("last-startup-hydrate-reason"));
        assertEquals("0", state.get("last-startup-hydrate-islands"));
        assertEquals("unknown", state.get("last-startup-hydrate-backend"));
        assertEquals("target-tick-start", state.get("last-startup-hydrate-policy"));
        assertEquals("cloudislands-api", state.get("last-startup-hydrate-state-owner-policy"));
        assertEquals("2026-07-04T00:00:00Z", state.get("last-startup-hydrate-at"));
    }
}
