package kr.lunaf.cloudislands.coreclient;

public record WarehouseItemView(String islandId, String materialKey, long amount, String updatedAt) {
    public WarehouseItemView(String materialKey, long amount) {
        this("", materialKey, amount, "");
    }

    public WarehouseItemView {
        islandId = islandId == null ? "" : islandId;
        materialKey = materialKey == null ? "" : materialKey;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
