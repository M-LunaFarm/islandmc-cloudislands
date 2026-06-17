package kr.seungmin.satisskyfactory.hook;

import java.util.function.Predicate;

public final class PlaceholderFeaturePolicy {
    private PlaceholderFeaturePolicy() {
    }

    public static boolean canResolve(String key, Predicate<String> featureEnabled) {
        if (!enabled(featureEnabled, "placeholders")) {
            return false;
        }
        if (key.equals("research") || key.startsWith("unlocked_")) {
            return enabled(featureEnabled, "research");
        }
        if (key.equals("debt") || key.equals("maintenance_status") || key.equals("maintenance_score")) {
            return enabled(featureEnabled, "maintenance");
        }
        if (key.equals("contracts_active") || key.equals("contract_slot_bonus")) {
            return enabled(featureEnabled, "contracts") && enabled(featureEnabled, "storage");
        }
        if (key.startsWith("storage_")) {
            return enabled(featureEnabled, "storage");
        }
        if (key.equals("resource_nodes") || key.equals("resource_node_count") || key.equals("nodes")) {
            return enabled(featureEnabled, "resource-nodes");
        }
        if (key.equals("factory_score") || key.equals("machines")
                || key.startsWith("power_") || key.startsWith("battery_") || key.equals("agriculture_boost")
                || key.equals("machine_limit_bonus")) {
            return enabled(featureEnabled, "machines");
        }
        return true;
    }

    private static boolean enabled(Predicate<String> featureEnabled, String feature) {
        return featureEnabled == null || featureEnabled.test(feature);
    }
}
