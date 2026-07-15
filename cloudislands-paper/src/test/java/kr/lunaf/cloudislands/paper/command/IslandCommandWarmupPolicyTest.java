package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

class IslandCommandWarmupPolicyTest {
    private final IslandCommandWarmupPolicy policy = new IslandCommandWarmupPolicy();

    @Test
    void pendingWarmupStaysActiveInsideSameBlockAndCancelsOnBlockMove() {
        UUID player = UUID.randomUUID();
        RecordingTask task = new RecordingTask();
        IslandCommandWarmupPolicy.BlockPosition start = new IslandCommandWarmupPolicy.BlockPosition("world", 10, 64, 10);

        policy.start(player, IslandCommandDelayPolicy.DelaySubject.HOME, start, task);

        assertTrue(policy.hasPending(player));
        assertTrue(policy.cancelOnMove(player, new IslandCommandWarmupPolicy.BlockPosition("world", 10, 64, 10), false).isEmpty());

        IslandCommandWarmupPolicy.PendingWarmup cancelled = policy
            .cancelOnMove(player, new IslandCommandWarmupPolicy.BlockPosition("world", 11, 64, 10), false)
            .orElseThrow();

        assertSame(IslandCommandDelayPolicy.DelaySubject.HOME, cancelled.subject());
        assertTrue(task.cancelled);
        assertFalse(policy.hasPending(player));
    }

    @Test
    void pendingWarmupCancelsWhenFallingInsideTheSameBlock() {
        UUID player = UUID.randomUUID();
        RecordingTask task = new RecordingTask();
        IslandCommandWarmupPolicy.BlockPosition start = new IslandCommandWarmupPolicy.BlockPosition("world", 10, 64, 10);

        policy.start(player, IslandCommandDelayPolicy.DelaySubject.HOME, start, task);

        IslandCommandWarmupPolicy.PendingWarmup cancelled = policy.cancelOnMove(player, start, true).orElseThrow();

        assertSame(IslandCommandDelayPolicy.DelaySubject.HOME, cancelled.subject());
        assertTrue(task.cancelled);
        assertFalse(policy.hasPending(player));
    }

    @Test
    void completeRemovesPendingWarmupWithoutCancellingFinishedTask() {
        UUID player = UUID.randomUUID();
        RecordingTask task = new RecordingTask();

        policy.start(player, IslandCommandDelayPolicy.DelaySubject.VISIT, new IslandCommandWarmupPolicy.BlockPosition("world", 0, 65, 0), task);

        assertTrue(policy.complete(player));
        assertFalse(policy.hasPending(player));
        assertFalse(task.cancelled);
    }

    @Test
    void combatLockExpiresAfterPolicyWindow() {
        UUID player = UUID.randomUUID();

        policy.markCombat(player, 1_000L);

        assertTrue(policy.combatBlocked(player, 1_000L + IslandCommandWarmupPolicy.COMBAT_LOCK_MILLIS - 1L));
        assertFalse(policy.combatBlocked(player, 1_000L + IslandCommandWarmupPolicy.COMBAT_LOCK_MILLIS));
    }

    @Test
    void clearCancelsPendingWarmupAndCombatState() {
        UUID player = UUID.randomUUID();
        RecordingTask task = new RecordingTask();

        policy.start(player, IslandCommandDelayPolicy.DelaySubject.CREATE, new IslandCommandWarmupPolicy.BlockPosition("world", 0, 64, 0), task);
        policy.markCombat(player, 1_000L);
        policy.clear(player);

        assertTrue(task.cancelled);
        assertFalse(policy.hasPending(player));
        assertFalse(policy.combatBlocked(player, 1_001L));
    }

    private static final class RecordingTask implements TaskHandle {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
