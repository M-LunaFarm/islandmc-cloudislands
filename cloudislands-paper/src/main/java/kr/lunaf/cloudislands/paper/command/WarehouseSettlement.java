package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import kr.lunaf.cloudislands.common.json.SimpleJson;

record WarehouseSettlement(UUID settlementId, UUID islandId, String materialKey, long amount, boolean deposit, String idempotencyKey, Phase phase) {
    private static final long FORMAT_VERSION = 2L;

    WarehouseSettlement {
        if (settlementId == null) {
            throw new IllegalArgumentException("settlementId is required");
        }
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        materialKey = materialKey == null ? "" : materialKey.trim().toUpperCase(java.util.Locale.ROOT);
        if (materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (!validIdempotencyKey(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must be 1-200 URL-safe characters");
        }
        phase = phase == null ? Phase.ESCROWED : phase;
    }

    WarehouseSettlement(UUID islandId, String materialKey, long amount, boolean deposit, String idempotencyKey) {
        this(UUID.randomUUID(), islandId, materialKey, amount, deposit, idempotencyKey, Phase.ESCROWED);
    }

    WarehouseSettlement settled() {
        return new WarehouseSettlement(settlementId, islandId, materialKey, amount, deposit, idempotencyKey, Phase.SETTLED);
    }

    String encode() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("version", FORMAT_VERSION);
        values.put("settlementId", settlementId.toString());
        values.put("islandId", islandId.toString());
        values.put("materialKey", materialKey);
        values.put("amount", amount);
        values.put("deposit", deposit);
        values.put("idempotencyKey", idempotencyKey);
        values.put("phase", phase.name());
        return SimpleJson.stringify(values);
    }

    static Optional<WarehouseSettlement> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<?, ?> values = SimpleJson.object(SimpleJson.parse(encoded));
            long version = SimpleJson.number(values.get("version"));
            if (version != 1L && version != FORMAT_VERSION) {
                return Optional.empty();
            }
            Object deposit = values.get("deposit");
            if (!(deposit instanceof Boolean depositValue)) {
                return Optional.empty();
            }
            String idempotencyKey = SimpleJson.text(values.get("idempotencyKey"));
            UUID settlementId = version == 1L
                ? UUID.nameUUIDFromBytes(("warehouse:" + idempotencyKey).getBytes(StandardCharsets.UTF_8))
                : UUID.fromString(SimpleJson.text(values.get("settlementId")));
            Phase phase = version == 1L ? Phase.ESCROWED : Phase.valueOf(SimpleJson.text(values.get("phase")));
            return Optional.of(new WarehouseSettlement(
                settlementId,
                UUID.fromString(SimpleJson.text(values.get("islandId"))),
                SimpleJson.text(values.get("materialKey")),
                SimpleJson.number(values.get("amount")),
                depositValue,
                idempotencyKey,
                phase
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
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

    enum Phase {
        ESCROWED,
        SETTLED
    }
}
