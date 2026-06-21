package kr.lunaf.cloudislands.coreclient;

public record WarehouseMutationView(boolean accepted, String code, String materialKey, long amount) {
    public WarehouseMutationView {
        code = code == null ? "" : code;
        materialKey = materialKey == null ? "" : materialKey;
    }
}
