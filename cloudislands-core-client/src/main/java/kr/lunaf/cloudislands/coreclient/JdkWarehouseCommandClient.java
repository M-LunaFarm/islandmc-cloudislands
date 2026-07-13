package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkWarehouseCommandClient implements WarehouseCommandClient {
    private final JdkCoreApiClient core;

    JdkWarehouseCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<WarehouseMutationView> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/warehouse/deposit", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey == null ? "" : materialKey, "amount", amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::warehouseMutation);
    }

    @Override
    public CompletableFuture<WarehouseMutationView> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/warehouse/withdraw", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey == null ? "" : materialKey, "amount", amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::warehouseMutation);
    }

    @Override
    public CompletableFuture<WarehouseSettlementResult> prepareSettlement(WarehouseSettlementView settlement) {
        if (settlement == null) {
            throw new IllegalArgumentException("settlement is required");
        }
        return core.postResultBody("/v1/players/warehouse-settlement/prepare", CoreJsonPayload.object(
            "settlementId", settlement.settlementId(),
            "playerUuid", settlement.playerUuid(),
            "islandId", settlement.islandId(),
            "materialKey", settlement.materialKey(),
            "amount", settlement.amount(),
            "direction", settlement.direction(),
            "idempotencyKey", settlement.idempotencyKey()
        )).thenApply(CoreResponseBody::value).thenApply(JdkWarehouseCommandClient::settlementResult);
    }

    @Override
    public CompletableFuture<WarehouseSettlementResult> escrowSettlement(UUID playerUuid, UUID settlementId) {
        requireId(playerUuid, "playerUuid");
        requireId(settlementId, "settlementId");
        return core.postResultBody("/v1/players/warehouse-settlement/escrow", CoreJsonPayload.object("playerUuid", playerUuid, "settlementId", settlementId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::settlementResult);
    }

    @Override
    public CompletableFuture<WarehouseSettlementResult> clearSettlement(UUID playerUuid, UUID settlementId) {
        requireId(playerUuid, "playerUuid");
        requireId(settlementId, "settlementId");
        return core.postResultBody("/v1/players/warehouse-settlement/clear", CoreJsonPayload.object("playerUuid", playerUuid, "settlementId", settlementId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::settlementResult);
    }

    private static WarehouseMutationView warehouseMutation(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = CoreJson.objectValue(root, "error");
        Map<?, ?> item = CoreJson.objectValue(root, "item");
        Map<?, ?> itemSource = item.isEmpty() ? root : item;
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = CoreJson.text(error, "code");
        }
        return new WarehouseMutationView(
            CoreJson.accepted(root),
            code,
            CoreJson.text(itemSource, "materialKey"),
            CoreJson.number(itemSource, "amount")
        );
    }

    static Optional<WarehouseSettlementView> settlement(Map<?, ?> value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WarehouseSettlementView(
            uuid(value, "settlementId"),
            uuid(value, "playerUuid"),
            uuid(value, "islandId"),
            CoreJson.text(value, "materialKey"),
            CoreJson.number(value, "amount"),
            CoreJson.text(value, "direction"),
            CoreJson.text(value, "state"),
            CoreJson.text(value, "idempotencyKey"),
            CoreJson.text(value, "ownerNodeId"),
            CoreJson.text(value, "createdAt"),
            CoreJson.text(value, "updatedAt")
        ));
    }

    private static WarehouseSettlementResult settlementResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = CoreJson.objectValue(root, "error");
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = CoreJson.text(error, "code");
        }
        return new WarehouseSettlementResult(CoreJson.accepted(root), code, settlement(CoreJson.objectValue(root, "settlement")).orElse(null));
    }

    private static UUID uuid(Map<?, ?> value, String key) {
        String text = CoreJson.text(value, key);
        if (text.isBlank()) {
            throw new CoreApiException("INVALID_CORE_JSON", "Warehouse settlement is missing " + key);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw new CoreApiException("INVALID_CORE_JSON", "Warehouse settlement has invalid " + key);
        }
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
