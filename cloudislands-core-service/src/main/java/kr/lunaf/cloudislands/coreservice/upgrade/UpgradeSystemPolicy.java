package kr.lunaf.cloudislands.coreservice.upgrade;

import java.util.List;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public final class UpgradeSystemPolicy {
    public static final String CONFIG_SOURCE_POLICY =
            "upgrades-are-loaded-from-rules-upgrades-yaml-or-configured-override-file";
    public static final String PURCHASE_FLOW_POLICY =
            "validate-rule>validate-max-level>resolve-cost>withdraw-economy-or-bank>write-upgrade-level>apply-effect";
    public static final String ECONOMY_BRIDGE_POLICY =
            "economy-integration-is-async-withdraw-deposit-balance-abstraction";

    private static final List<UpgradeType> GOAL_UPGRADE_TYPES = List.of(
            UpgradeType.ISLAND_SIZE,
            UpgradeType.MAX_MEMBERS,
            UpgradeType.MAX_WARPS,
            UpgradeType.HOPPER_LIMIT,
            UpgradeType.SPAWNER_LIMIT,
            UpgradeType.GENERATOR_LEVEL,
            UpgradeType.MOB_LIMIT,
            UpgradeType.CROP_GROWTH,
            UpgradeType.FLY_ACCESS,
            UpgradeType.BANK_LIMIT
    );

    private static final List<String> GOAL_CONFIG_EXAMPLES = List.of(
            "size:1:size=100:cost=0",
            "size:2:size=150:cost=10000",
            "size:3:size=200:cost=50000",
            "members:1:max-members=3:cost=0",
            "members:2:max-members=5:cost=25000",
            "members:3:max-members=8:cost=75000",
            "hoppers:1:max-hoppers=50:cost=0",
            "hoppers:2:max-hoppers=100:cost=30000"
    );

    private UpgradeSystemPolicy() {
    }

    public static List<UpgradeType> goalUpgradeTypes() {
        return GOAL_UPGRADE_TYPES;
    }

    public static List<String> goalConfigExamples() {
        return GOAL_CONFIG_EXAMPLES;
    }

    public static boolean goalUpgradeType(UpgradeType type) {
        return GOAL_UPGRADE_TYPES.contains(type);
    }
}
