package kr.lunaf.cloudislands.coreclient;

public record UpgradeRuleView(String key, String type, long maxLevel, String baseCost, String multiplier) {
    public UpgradeRuleView {
        key = key == null ? "" : key;
        type = type == null ? "" : type;
        baseCost = baseCost == null ? "" : baseCost;
        multiplier = multiplier == null || multiplier.isBlank() ? "1" : multiplier;
    }
}
