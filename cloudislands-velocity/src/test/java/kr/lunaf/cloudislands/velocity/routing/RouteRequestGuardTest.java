package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteRequestGuardTest {
    @Test
    void rejectsRepeatedRequestsInsideCooldownWindow() {
        UUID playerUuid = UUID.randomUUID();
        RouteRequestGuard guard = new RouteRequestGuard(1_500L, Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC));

        assertTrue(guard.allow(playerUuid));
        assertFalse(guard.allow(playerUuid));
    }

    @Test
    void tracksCooldownsPerAction() {
        UUID playerUuid = UUID.randomUUID();
        RouteRequestGuard guard = new RouteRequestGuard(1_500L, Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC));

        assertTrue(guard.allow(playerUuid, "cloudislands.island.home.cooldown"));
        assertTrue(guard.allow(playerUuid, "cloudislands.island.visit.cooldown"));
        assertFalse(guard.allow(playerUuid, "cloudislands.island.home.cooldown"));
    }

    @Test
    void bypassDoesNotRecordCooldownState() {
        UUID playerUuid = UUID.randomUUID();
        RouteRequestGuard guard = new RouteRequestGuard(1_500L, Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC));

        assertTrue(guard.allow(playerUuid, "cloudislands.island.create.cooldown", true));
        assertTrue(guard.allow(playerUuid, "cloudislands.island.create.cooldown"));
        assertFalse(guard.allow(playerUuid, "cloudislands.island.create.cooldown"));
    }

    @Test
    void allowsAgainAfterStateIsCleared() {
        UUID playerUuid = UUID.randomUUID();
        RouteRequestGuard guard = new RouteRequestGuard(1_500L, Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC));

        assertTrue(guard.allow(playerUuid, "cloudislands.island.home.cooldown"));
        assertTrue(guard.allow(playerUuid, "cloudislands.island.visit.cooldown"));
        guard.clear(playerUuid);

        assertTrue(guard.allow(playerUuid, "cloudislands.island.home.cooldown"));
        assertTrue(guard.allow(playerUuid, "cloudislands.island.visit.cooldown"));
    }
}
