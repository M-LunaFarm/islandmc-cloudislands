package kr.seungmin.satisskyfactory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FactoryIslandNumericSafetyTest {
    @Test
    void progressionAndDebtCountersStayWithinNonNegativeLongRange() {
        FactoryIsland island = new FactoryIsland(UUID.randomUUID(), UUID.randomUUID());

        island.researchPoints(Long.MAX_VALUE - 2L);
        assertEquals(Long.MAX_VALUE, island.addResearchPoints(10L));
        assertEquals(Long.MAX_VALUE, island.addResearchPoints(1L));
        assertEquals(0L, island.addResearchPoints(Long.MIN_VALUE));

        island.reputation(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, island.addReputation(1L));
        assertEquals(0L, island.addReputation(Long.MIN_VALUE));

        island.maintenanceDebt(Long.MAX_VALUE - 1L);
        assertEquals(Long.MAX_VALUE, island.addMaintenanceDebt(10L));
        assertEquals(0L, island.addMaintenanceDebt(Long.MIN_VALUE));

        island.researchPoints(-1L);
        island.reputation(-1L);
        island.maintenanceDebt(-1L);
        assertEquals(0L, island.researchPoints());
        assertEquals(0L, island.reputation());
        assertEquals(0L, island.maintenanceDebt());
    }
}
