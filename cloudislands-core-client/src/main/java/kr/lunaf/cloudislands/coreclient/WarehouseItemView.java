package kr.lunaf.cloudislands.coreclient;

public record WarehouseItemView(String materialKey, long amount) {
    public WarehouseItemView {
        materialKey = materialKey == null ? "" : materialKey;
    }
}
