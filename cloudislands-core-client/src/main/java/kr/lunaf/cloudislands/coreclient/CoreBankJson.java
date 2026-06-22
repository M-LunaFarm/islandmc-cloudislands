package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

final class CoreBankJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private CoreBankJson() {
    }

    static IslandBankSnapshot snapshot(String body) {
        return snapshot(CoreJson.object(body));
    }

    static IslandBankSnapshot snapshot(CoreResponseBody body) {
        return snapshot(body.value());
    }

    static IslandBankChangeSnapshot mutation(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> bank = CoreJson.objectValue(root, "bank");
        boolean accepted = CoreJson.accepted(root);
        String code = CoreJson.code(root, "", accepted);
        return new IslandBankChangeSnapshot(accepted, code, bank.isEmpty() ? snapshot(root) : snapshot(bank));
    }

    static IslandBankChangeSnapshot mutation(CoreResponseBody body) {
        return mutation(body.value());
    }

    static BankMutationView mutationView(String body) {
        IslandBankChangeSnapshot change = mutation(body);
        IslandBankSnapshot bank = change.bank();
        return new BankMutationView(
            change.accepted(),
            change.code(),
            bank == null || bank.islandId() == null ? "" : bank.islandId().toString(),
            bank == null ? "0" : bank.balance(),
            bank == null || bank.updatedAt() == null ? "" : bank.updatedAt().toString()
        );
    }

    private static IslandBankSnapshot snapshot(Map<?, ?> values) {
        return new IslandBankSnapshot(
            uuid(CoreJson.text(values, "islandId")),
            balance(CoreJson.text(values, "balance")),
            instant(CoreJson.text(values, "updatedAt"))
        );
    }

    private static String balance(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? EMPTY_UUID : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return EMPTY_UUID;
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }
}
