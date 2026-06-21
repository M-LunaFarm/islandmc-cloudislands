package kr.lunaf.cloudislands.coreclient;

public record ProgressionUpgradePurchaseView(boolean accepted, String code, String islandId, String upgradeKey, String type, long level, String cost, String updatedAt) {
    public ProgressionUpgradePurchaseView(boolean accepted, String code, String upgradeKey, long level, String cost) {
        this(accepted, code, "", upgradeKey, "", level, cost, "");
    }

    public ProgressionUpgradePurchaseView {
        code = code == null ? "" : code;
        islandId = islandId == null ? "" : islandId;
        upgradeKey = upgradeKey == null ? "" : upgradeKey;
        type = type == null ? "" : type;
        cost = cost == null || cost.isBlank() ? "0" : cost;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
