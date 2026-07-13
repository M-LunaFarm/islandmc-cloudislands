package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;

record WarehouseSettlement(UUID islandId, String materialKey, long amount, boolean deposit, String idempotencyKey) {
    private static final long FORMAT_VERSION = 1L;

    WarehouseSettlement {
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
    }

    String encode() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("version", FORMAT_VERSION);
        values.put("islandId", islandId.toString());
        values.put("materialKey", materialKey);
        values.put("amount", amount);
        values.put("deposit", deposit);
        values.put("idempotencyKey", idempotencyKey);
        return SimpleJson.stringify(values);
    }

    static Optional<WarehouseSettlement> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<?, ?> values = SimpleJson.object(SimpleJson.parse(encoded));
            if (SimpleJson.number(values.get("version")) != FORMAT_VERSION) {
                return Optional.empty();
            }
            Object deposit = values.get("deposit");
            if (!(deposit instanceof Boolean depositValue)) {
                return Optional.empty();
            }
            return Optional.of(new WarehouseSettlement(
                UUID.fromString(SimpleJson.text(values.get("islandId"))),
                SimpleJson.text(values.get("materialKey")),
                SimpleJson.number(values.get("amount")),
                depositValue,
                SimpleJson.text(values.get("idempotencyKey"))
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
}
