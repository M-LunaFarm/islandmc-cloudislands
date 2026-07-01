package kr.lunaf.cloudislands.migration;

public record MigrationWarehouseItem(String materialKey, long amount) {
    public MigrationWarehouseItem {
        materialKey = materialKey == null ? "" : materialKey.trim().toLowerCase();
        amount = Math.max(0L, amount);
    }
}
