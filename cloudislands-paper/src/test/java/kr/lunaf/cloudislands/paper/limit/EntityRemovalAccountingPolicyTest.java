package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Test;

class EntityRemovalAccountingPolicyTest {
    @Test
    void recordsPermanentRemovalsThatDoNotHaveAnExistingAcceptedRemovalEvent() {
        assertTrue(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.DESPAWN));
        assertTrue(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.ENTER_BLOCK));
        assertTrue(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.OUT_OF_WORLD));
        assertTrue(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.TRANSFORMATION));
    }

    @Test
    void ignoresTemporaryUnloadAndRemovalCausesAlreadyAccountedElsewhere() {
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.UNLOAD));
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.DEATH));
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.EXPLODE));
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.HIT));
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.PLUGIN));
        assertFalse(EntityRemovalAccountingPolicy.records(EntityRemoveEvent.Cause.DISCARD));
    }
}
