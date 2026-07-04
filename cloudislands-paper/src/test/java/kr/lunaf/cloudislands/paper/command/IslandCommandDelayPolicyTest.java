package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandCommandDelayPolicyTest {
    private final IslandCommandDelayPolicy policy = new IslandCommandDelayPolicy();

    @Test
    void repeatedHomeCommandIsBlockedUntilCooldownExpires() {
        UUID player = UUID.randomUUID();

        IslandCommandDelayPolicy.Decision first = policy.evaluate(player, "home", false, false, 1_000L);
        IslandCommandDelayPolicy.Decision second = policy.evaluate(player, "home", false, false, 2_000L);
        IslandCommandDelayPolicy.Decision afterCooldown = policy.evaluate(player, "home", false, false, 4_100L);

        assertTrue(first.allowed());
        assertSame(IslandCommandDelayPolicy.DelaySubject.HOME, first.subject());
        assertTrue(first.warmupRequired());
        assertFalse(second.allowed());
        assertEquals(2L, second.secondsRemaining());
        assertTrue(afterCooldown.allowed());
    }

    @Test
    void cooldownBypassDoesNotCreateLaterCooldownBlock() {
        UUID player = UUID.randomUUID();

        IslandCommandDelayPolicy.Decision bypassed = policy.evaluate(player, "visit", true, false, 1_000L);
        IslandCommandDelayPolicy.Decision immediate = policy.evaluate(player, "visit", false, false, 1_100L);

        assertTrue(bypassed.allowed());
        assertTrue(bypassed.warmupRequired());
        assertTrue(immediate.allowed(), "bypassed commands must not record a cooldown for the next command");
    }

    @Test
    void warmupBypassKeepsCooldownButSuppressesWaitingState() {
        UUID player = UUID.randomUUID();

        IslandCommandDelayPolicy.Decision first = policy.evaluate(player, "create", false, true, 1_000L);
        IslandCommandDelayPolicy.Decision second = policy.evaluate(player, "생성", false, true, 2_000L);

        assertTrue(first.allowed());
        assertSame(IslandCommandDelayPolicy.DelaySubject.CREATE, first.subject());
        assertFalse(first.warmupRequired());
        assertFalse(second.allowed(), "warmup bypass must not bypass cooldown enforcement");
    }

    @Test
    void KoreanAndSnapshotAliasesMapToDelaySubjects() {
        assertSame(IslandCommandDelayPolicy.DelaySubject.CREATE, subject("생성"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.HOME, subject("홈"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.VISIT, subject("랜덤방문"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.DELETE, subject("삭제"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.RESET, subject("리셋"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.SNAPSHOT, subject("snapshot-create"));
        assertSame(IslandCommandDelayPolicy.DelaySubject.RESTORE, subject("rollback"));
    }

    @Test
    void clearRemovesPlayerCooldownState() {
        UUID player = UUID.randomUUID();

        assertTrue(policy.evaluate(player, "restore", false, false, 1_000L).allowed());
        assertFalse(policy.evaluate(player, "restore", false, false, 1_100L).allowed());

        policy.clear(player);

        assertTrue(policy.evaluate(player, "restore", false, false, 1_200L).allowed());
    }

    @Test
    void unknownOrConsoleSubjectsAreAllowedWithoutDelayState() {
        assertTrue(policy.evaluate(UUID.randomUUID(), "unknown", false, false, 1_000L).allowed());
        assertTrue(policy.evaluate(null, "home", false, false, 1_000L).allowed());
    }

    private IslandCommandDelayPolicy.DelaySubject subject(String command) {
        return policy.evaluate(UUID.randomUUID(), command, true, true, 1_000L).subject();
    }
}
