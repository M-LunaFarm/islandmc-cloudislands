package kr.lunaf.cloudislands.coreclient;

public record WarehouseSettlementResult(boolean accepted, String code, WarehouseSettlementView settlement) {
    public WarehouseSettlementResult {
        code = code == null ? "" : code;
    }
}
