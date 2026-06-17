package kr.seungmin.satisskyfactory.hook;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderFeaturePolicyTest {
    @Test
    void placeholdersFeatureDisablesEveryPlaceholderValue() {
        Predicate<String> features = feature -> !"placeholders".equals(feature);

        assertFalse(PlaceholderFeaturePolicy.canResolve("island_uuid", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("tier", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("storage_used", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("machines", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("contracts_active", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("unlocked_logistics", features));
    }

    @Test
    void storageFeatureOnlyBlocksStorageBackedPlaceholders() {
        Predicate<String> features = feature -> !"storage".equals(feature);

        assertFalse(PlaceholderFeaturePolicy.canResolve("storage_used", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("contracts_active", features));
        assertTrue(PlaceholderFeaturePolicy.canResolve("island_uuid", features));
        assertTrue(PlaceholderFeaturePolicy.canResolve("machines", features));
    }

    @Test
    void machinesFeatureBlocksPowerAndMachinePlaceholders() {
        Predicate<String> features = feature -> !"machines".equals(feature);

        assertFalse(PlaceholderFeaturePolicy.canResolve("machines", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("factory_score", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("power_ratio", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("battery_percent", features));
        assertFalse(PlaceholderFeaturePolicy.canResolve("machine_limit_bonus", features));
        assertTrue(PlaceholderFeaturePolicy.canResolve("island_uuid", features));
        assertTrue(PlaceholderFeaturePolicy.canResolve("storage_capacity", features));
    }
}
