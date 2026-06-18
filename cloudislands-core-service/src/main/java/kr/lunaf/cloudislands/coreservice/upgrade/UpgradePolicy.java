package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;

public final class UpgradePolicy {
    public static final String CONFIG_DRIVEN_POLICY = "config-driven-upgrade-rules-with-bank-withdraw-and-limit-application";
    public static final String SUPPORTED_TYPE_POLICY = "ISLAND_SIZE,MAX_MEMBERS,MAX_WARPS,HOPPER_LIMIT,SPAWNER_LIMIT,GENERATOR_LEVEL,MOB_LIMIT,CROP_GROWTH,FLY_ACCESS,REDSTONE_LIMIT,BANK_LIMIT";
    public static final String EFFECT_APPLICATION_POLICY = "upgrade-levels-drive-size-members-warps-hoppers-spawners-generator-mob-crop-fly-redstone-bank-limits";

    private final Map<String, UpgradeRule> rules;

    public UpgradePolicy() {
        Map<String, UpgradeRule> defaults = new LinkedHashMap<>();
        defaults.put("size", new UpgradeRule("size", UpgradeType.ISLAND_SIZE, 5, new BigDecimal("10000"), new BigDecimal("2")));
        defaults.put("members", new UpgradeRule("members", UpgradeType.MAX_MEMBERS, 5, new BigDecimal("7500"), new BigDecimal("2")));
        defaults.put("warps", new UpgradeRule("warps", UpgradeType.MAX_WARPS, 5, new BigDecimal("5000"), new BigDecimal("2")));
        defaults.put("hoppers", new UpgradeRule("hoppers", UpgradeType.HOPPER_LIMIT, 5, new BigDecimal("30000"), new BigDecimal("2")));
        defaults.put("spawners", new UpgradeRule("spawners", UpgradeType.SPAWNER_LIMIT, 5, new BigDecimal("50000"), new BigDecimal("2")));
        defaults.put("generator", new UpgradeRule("generator", UpgradeType.GENERATOR_LEVEL, 5, new BigDecimal("25000"), new BigDecimal("2")));
        defaults.put("mob", new UpgradeRule("mob", UpgradeType.MOB_LIMIT, 5, new BigDecimal("20000"), new BigDecimal("2")));
        defaults.put("crop", new UpgradeRule("crop", UpgradeType.CROP_GROWTH, 5, new BigDecimal("15000"), new BigDecimal("2")));
        defaults.put("fly", new UpgradeRule("fly", UpgradeType.FLY_ACCESS, 1, new BigDecimal("100000"), BigDecimal.ONE));
        defaults.put("redstone", new UpgradeRule("redstone", UpgradeType.REDSTONE_LIMIT, 5, new BigDecimal("20000"), new BigDecimal("2")));
        defaults.put("bank", new UpgradeRule("bank", UpgradeType.BANK_LIMIT, 5, new BigDecimal("10000"), new BigDecimal("2")));
        this.rules = Map.copyOf(defaults);
    }

    public UpgradePolicy(Map<String, UpgradeRule> rules) {
        this.rules = Map.copyOf(rules);
    }

    public UpgradeRule rule(String upgradeKey) {
        return rules.get(upgradeKey.toLowerCase());
    }

    public List<UpgradeRule> list() {
        return List.copyOf(rules.values());
    }

    public static UpgradeType typeFor(String upgradeKey) {
        if (upgradeKey.toLowerCase().startsWith("generator:")) {
            return UpgradeType.GENERATOR_LEVEL;
        }
        return switch (upgradeKey.toLowerCase()) {
            case "size" -> UpgradeType.ISLAND_SIZE;
            case "members" -> UpgradeType.MAX_MEMBERS;
            case "warps" -> UpgradeType.MAX_WARPS;
            case "hoppers" -> UpgradeType.HOPPER_LIMIT;
            case "spawners" -> UpgradeType.SPAWNER_LIMIT;
            case "generator" -> UpgradeType.GENERATOR_LEVEL;
            case "mob" -> UpgradeType.MOB_LIMIT;
            case "crop" -> UpgradeType.CROP_GROWTH;
            case "fly" -> UpgradeType.FLY_ACCESS;
            case "redstone" -> UpgradeType.REDSTONE_LIMIT;
            case "bank" -> UpgradeType.BANK_LIMIT;
            default -> UpgradeType.ISLAND_SIZE;
        };
    }
}
