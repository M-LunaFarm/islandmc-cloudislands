package kr.lunaf.cloudislands.api.upgrade;

public record UpgradeRuleSnapshot(String upgradeKey, UpgradeType type, int maxLevel, String baseCost, String multiplier) {}
