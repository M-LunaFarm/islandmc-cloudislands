package kr.lunaf.cloudislands.coreclient;

public record ProgressionUpgradePurchaseView(boolean accepted, String code, String upgradeKey, long level, String cost) {
    public ProgressionUpgradePurchaseView {
        code = code == null ? "" : code;
        upgradeKey = upgradeKey == null ? "" : upgradeKey;
        cost = cost == null || cost.isBlank() ? "0" : cost;
    }
}
