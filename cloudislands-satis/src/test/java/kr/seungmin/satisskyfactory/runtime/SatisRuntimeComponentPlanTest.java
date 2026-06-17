package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeComponentPlanTest {
    @Test
    void addonDisabledSkipsEveryActiveRuntimeComponent() {
        SatisRuntimeComponentPlan plan = new SatisRuntimeComponentPlan(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );

        assertEquals("none", plan.activeComponentsMetadata());
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
        assertTrue(plan.blockedComponentsMetadata().contains("dirty-save:data-writes-disabled"));
        assertTrue(plan.blockedComponentsMetadata().contains("core-api-state-writer:addon-state-feature-disabled"));
    }

    @Test
    void machinesDisabledLeavesCommandsAndDirtySaveIndependent() {
        SatisRuntimeComponentPlan plan = new SatisRuntimeComponentPlan(
                true,
                true,
                false,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );

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
        SatisRuntimeComponentPlan plan = new SatisRuntimeComponentPlan(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true
        );

        assertTrue(plan.skippedComponentsMetadata().contains("placeholder-expansion:placeholderapi-not-installed"));
        assertTrue(plan.blockedComponentsMetadata().contains("placeholders:placeholderapi-not-installed"));
    }

    @Test
    void coreApiWriterReportsUnavailableApiSeparatelyFromFeatureGate() {
        SatisRuntimeComponentPlan plan = new SatisRuntimeComponentPlan(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false
        );

        assertTrue(plan.skippedComponentsMetadata().contains("core-api-state-writer"));
        assertTrue(plan.blockedComponentsMetadata().contains("core-api-state-writer:cloudislands-api-unavailable"));
    }
}
