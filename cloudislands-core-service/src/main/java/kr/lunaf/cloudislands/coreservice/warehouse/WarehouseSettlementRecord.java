package kr.lunaf.cloudislands.coreservice.warehouse;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;

public record WarehouseSettlementRecord(
    UUID settlementId,
    UUID playerUuid,
    UUID islandId,
    String materialKey,
    long amount,
    Direction direction,
    State state,
    String idempotencyKey,
    String ownerNodeId,
    Instant createdAt,
    Instant updatedAt
) {
    public WarehouseSettlementRecord {
        if (settlementId == null || playerUuid == null || islandId == null) {
            throw new IllegalArgumentException("settlement, player, and island ids are required");
        }
        materialKey = IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey);
        if (materialKey.isBlank() || amount <= 0L) {
            throw new IllegalArgumentException("material and positive amount are required");
        }
        if (direction == null || state == null) {
            throw new IllegalArgumentException("direction and state are required");
        }
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (!validIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must be 1-200 URL-safe characters");
        }
        ownerNodeId = ownerNodeId == null ? "" : ownerNodeId.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public WarehouseSettlementRecord escrowed(Instant now) {
        return new WarehouseSettlementRecord(settlementId, playerUuid, islandId, materialKey, amount, direction, State.ESCROWED, idempotencyKey, ownerNodeId, createdAt, now);
    }

    public boolean sameOperation(WarehouseSettlementRecord other) {
        return other != null
            && settlementId.equals(other.settlementId)
            && playerUuid.equals(other.playerUuid)
            && islandId.equals(other.islandId)
            && materialKey.equals(other.materialKey)
            && amount == other.amount
            && direction == other.direction
            && idempotencyKey.equals(other.idempotencyKey);
    }

    private static boolean validIdempotencyKey(String key) {
        if (key.isEmpty() || key.length() > 200) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            boolean alphaNumeric = character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9';
            if (!alphaNumeric && character != '.' && character != '_' && character != ':' && character != '-') {
                return false;
            }
        }
        return true;
    }

    public enum Direction {
        DEPOSIT,
        WITHDRAW;

        public static Direction parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid warehouse settlement direction", exception);
            }
        }
    }

    public enum State {
        PREPARED,
        ESCROWED
    }
}
