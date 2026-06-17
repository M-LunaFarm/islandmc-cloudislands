package kr.seungmin.satisskyfactory.hook;

import java.util.Set;
import java.util.function.Predicate;

public final class PlaceholderFeaturePolicy {
    private static final Set<String> BASE_KEYS = Set.of(
            "island_uuid",
            "tier",
            "reputation"
    );
    private static final Set<String> MAINTENANCE_KEYS = Set.of(
            "debt",
            "maintenance_status",
            "maintenance_score"
    );
    private static final Set<String> CONTRACT_KEYS = Set.of(
            "contracts_active",
            "contract_slot_bonus"
    );
    private static final Set<String> STORAGE_KEYS = Set.of(
            "storage_used",
            "storage_capacity",
            "storage_free"
    );
    private static final Set<String> RESOURCE_NODE_KEYS = Set.of(
            "resource_nodes",
            "resource_node_count",
            "nodes"
    );
    private static final Set<String> MACHINE_KEYS = Set.of(
            "factory_score",
            "machines",
            "power_ratio",
            "power_generation",
            "power_consumption",
            "battery_stored",
            "battery_capacity",
            "battery_percent",
            "agriculture_boost",
            "machine_limit_bonus"
    );

    private PlaceholderFeaturePolicy() {
    }

    public static boolean canResolve(String key, Predicate<String> featureEnabled) {
        if (!enabled(featureEnabled, "placeholders")) {
            return false;
        }
        if (BASE_KEYS.contains(key)) {
            return true;
        }
        if (key.equals("research") || key.startsWith("unlocked_")) {
            return enabled(featureEnabled, "research");
        }
        if (MAINTENANCE_KEYS.contains(key)) {
            return enabled(featureEnabled, "maintenance");
        }
        if (CONTRACT_KEYS.contains(key)) {
            return enabled(featureEnabled, "contracts") && enabled(featureEnabled, "storage");
        }
        if (STORAGE_KEYS.contains(key)) {
            return enabled(featureEnabled, "storage");
        }
        if (RESOURCE_NODE_KEYS.contains(key)) {
            return enabled(featureEnabled, "resource-nodes");
        }
        if (MACHINE_KEYS.contains(key)) {
            return enabled(featureEnabled, "machines");
        }
        return false;
    }

    public static String exposedKeys() {
        return "island_uuid,tier,reputation,research,unlocked_<research>,debt,maintenance_status,maintenance_score,"
                + "contracts_active,contract_slot_bonus,storage_used,storage_capacity,storage_free,"
                + "resource_nodes,resource_node_count,nodes,factory_score,machines,power_ratio,power_generation,"
                + "power_consumption,battery_stored,battery_capacity,battery_percent,agriculture_boost,machine_limit_bonus";
    }

    public static String deniedInternalFields() {
        return "server,node,world,cell,coordinates,placement,route,backend-storage-key";
    }

    public static String exposurePolicy() {
        return "allow-listed-public-island-metrics-only-no-server-node-world-cell-coordinate-placement-or-route-identifiers";
    }

    private static boolean enabled(Predicate<String> featureEnabled, String feature) {
        return featureEnabled == null || featureEnabled.test(feature);
    }
}
