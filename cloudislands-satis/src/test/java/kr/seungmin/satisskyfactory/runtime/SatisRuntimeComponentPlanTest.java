package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeComponentPlanTest {
    @Test
    void addonDisabledSkipsEveryActiveRuntimeComponent() {
        SatisRuntimeComponentPlan plan = plan(all(false));

        assertEquals("cloudislands-api-required-no-standalone-island-runtime", SatisRuntimeComponentPlan.STANDALONE_ISLAND_RUNTIME_POLICY);
        assertEquals("none", plan.activeComponentsMetadata());
        assertEquals("disabled-no-standalone-island-management", plan.islandRuntimeAuthorityMetadata());
        assertEquals("addon-disabled", plan.commandBlockReason());
        assertTrue(plan.skippedComponentsMetadata().contains("commands"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-ticker"));
        assertTrue(plan.skippedComponentsMetadata().contains("gui-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("lifecycle-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("maintenance-ticker"));
        assertTrue(plan.skippedComponentsMetadata().contains("placeholder-expansion"));
        assertTrue(plan.skippedComponentsMetadata().contains("dirty-save"));
        assertTrue(plan.skippedComponentsMetadata().contains("core-api-state-writer"));
        assertTrue(plan.blockedComponentsMetadata().contains("commands:addon-disabled"));
        assertTrue(plan.blockedComponentsMetadata().contains("dirty-save:addon-disabled"));
        assertTrue(plan.blockedComponentsMetadata().contains("core-api-state-writer:addon-disabled"));
    }

    @Test
    void addonDisabledDominatesStaleRegisteredRuntimeState() {
        boolean[] values = all(true);
        values[0] = false;
        SatisRuntimeComponentPlan plan = plan(values);

        assertEquals("none", plan.activeComponentsMetadata());
        assertEquals("disabled-no-standalone-island-management", plan.islandRuntimeAuthorityMetadata());
        assertEquals("addon-disabled", plan.commandBlockReason());
        assertTrue(plan.skippedComponentsMetadata().contains("commands"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-ticker"));
        assertTrue(plan.blockedComponentsMetadata().contains("commands:addon-disabled"));
        assertTrue(plan.blockedComponentsMetadata().contains("core-api-state-writer:addon-disabled"));
    }

    @Test
    void machinesDisabledLeavesCommandsAndDirtySaveIndependent() {
        boolean[] values = all(true);
        values[2] = false;
        values[5] = false;
        values[11] = false;
        SatisRuntimeComponentPlan plan = plan(values);

        assertEquals("cloudislands-api", plan.islandRuntimeAuthorityMetadata());
        assertEquals("none", plan.commandBlockReason());
        assertTrue(plan.activeComponentsMetadata().contains("commands"));
        assertTrue(plan.activeComponentsMetadata().contains("dirty-save"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-ticker"));
        assertTrue(plan.blockedComponentsMetadata().contains("machine-listener:machines-feature-disabled"));
        assertTrue(plan.blockedComponentsMetadata().contains("machine-ticker:machines-feature-disabled"));
    }

    @Test
    void placeholderApiMissingSkipsOnlyPlaceholderExpansionWhenFeatureIsEnabled() {
        boolean[] values = all(true);
        values[7] = false;
        values[21] = false;
        SatisRuntimeComponentPlan plan = plan(values);

        assertTrue(plan.skippedComponentsMetadata().contains("placeholder-expansion:placeholderapi-not-installed"));
        assertTrue(plan.blockedComponentsMetadata().contains("placeholders:placeholderapi-not-installed"));
    }

    @Test
    void coreApiWriterReportsUnavailableApiSeparatelyFromFeatureGate() {
        boolean[] values = all(true);
        values[8] = false;
        values[24] = false;
        SatisRuntimeComponentPlan plan = plan(values);

        assertEquals("none", plan.activeComponentsMetadata());
        assertEquals("blocked-cloudislands-api-unavailable-no-standalone-island-management", plan.islandRuntimeAuthorityMetadata());
        assertEquals("cloudislands-api-unavailable-no-standalone-island-management", plan.commandBlockReason());
        assertTrue(plan.skippedComponentsMetadata().contains("commands"));
        assertTrue(plan.skippedComponentsMetadata().contains("core-api-state-writer"));
        assertTrue(plan.blockedComponentsMetadata().contains("commands:cloudislands-api-unavailable"));
        assertTrue(plan.blockedComponentsMetadata().contains("core-api-state-writer:cloudislands-api-unavailable"));
        assertEquals("all:cloudislands-api-unavailable-no-standalone-island-management", plan.featureBlockReasonsMetadata());
    }

    private boolean[] all(boolean value) {
        boolean[] values = new boolean[25];
        Arrays.fill(values, value);
        return values;
    }

    private SatisRuntimeComponentPlan plan(boolean[] values) {
        assertEquals(25, values.length);
        return new SatisRuntimeComponentPlan(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                values[6],
                values[7],
                values[8],
                values[9],
                values[10],
                values[11],
                values[12],
                values[13],
                values[14],
                values[15],
                values[16],
                values[17],
                values[18],
                values[19],
                values[20],
                values[21],
                values[22],
                values[23],
                values[24]
        );
    }
}
