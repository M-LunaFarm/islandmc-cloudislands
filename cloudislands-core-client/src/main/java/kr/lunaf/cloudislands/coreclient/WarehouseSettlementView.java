package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;

public record WarehouseSettlementView(
    UUID settlementId,
    UUID playerUuid,
    UUID islandId,
    String materialKey,
    long amount,
    String direction,
    String state,
    String idempotencyKey,
    String ownerNodeId,
    String createdAt,
    String updatedAt
) {
    public WarehouseSettlementView {
        materialKey = materialKey == null ? "" : materialKey;
        direction = direction == null ? "" : direction;
        state = state == null ? "" : state;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        ownerNodeId = ownerNodeId == null ? "" : ownerNodeId;
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
