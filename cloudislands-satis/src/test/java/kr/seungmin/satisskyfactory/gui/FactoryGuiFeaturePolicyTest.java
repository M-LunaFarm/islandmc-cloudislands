package kr.seungmin.satisskyfactory.gui;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryGuiFeaturePolicyTest {
    @Test
    void guiFeatureDisablesEveryStaleGuiAction() {
        Predicate<String> features = feature -> !"gui".equals(feature);

        assertFalse(FactoryGuiFeaturePolicy.canHandle("main_storage", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("sell_market_item", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("complete_contract", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("unlock_research", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("reclaim_machine", features));
        assertEquals(Optional.of("gui"), FactoryGuiFeaturePolicy.blockedFeature("unknown_future_action", features));
    }

    @Test
    void storageFeatureBlocksStorageBackedGuiActions() {
        Predicate<String> features = disabled("storage");

        assertFalse(FactoryGuiFeaturePolicy.canHandle("main_storage", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("deposit_hand", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("sell_market_item", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("complete_contract", features));
        assertTrue(FactoryGuiFeaturePolicy.canHandle("main_research", features));
        assertEquals(Optional.of("storage"), FactoryGuiFeaturePolicy.blockedFeature("sell_market_item", features));
    }

    @Test
    void primaryFeatureIsReportedBeforeStorageDependency() {
        Predicate<String> features = disabled("market", "storage");

        assertEquals(Optional.of("market"), FactoryGuiFeaturePolicy.blockedFeature("sell_market_item", features));
    }

    @Test
    void emergencyContractRequiresMaintenanceAfterContractsAndStorage() {
        assertEquals(Optional.of("contracts"), FactoryGuiFeaturePolicy.blockedFeature("complete_emergency", disabled("contracts", "storage", "maintenance")));
        assertEquals(Optional.of("storage"), FactoryGuiFeaturePolicy.blockedFeature("complete_emergency", disabled("storage", "maintenance")));
        assertEquals(Optional.of("maintenance"), FactoryGuiFeaturePolicy.blockedFeature("complete_emergency", disabled("maintenance")));
    }

    @Test
    void machineInventoryActionsRequireMachinesFeature() {
        Predicate<String> features = disabled("machines");

        assertFalse(FactoryGuiFeaturePolicy.canHandle("deposit_machine_input", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("withdraw_machine_output", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("select_recipe", features));
        assertFalse(FactoryGuiFeaturePolicy.canHandle("reclaim_machine", features));
        assertTrue(FactoryGuiFeaturePolicy.canHandle("main_storage", features));
    }

    private Predicate<String> disabled(String... disabledFeatures) {
        Set<String> disabled = Set.of(disabledFeatures);
        return feature -> !disabled.contains(feature);
    }
}
