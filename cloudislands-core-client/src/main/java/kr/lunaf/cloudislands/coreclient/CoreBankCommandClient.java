package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreBankCommandClient implements BankCommandClient {
    private final CoreApiClient delegate;

    public CoreBankCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<BankMutationView> deposit(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.depositIslandBank(islandId, actorUuid, amount == null ? "" : amount)
            .thenApply(CoreBankCommandClient::bankMutation);
    }

    @Override
    public CompletableFuture<BankMutationView> withdraw(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.withdrawIslandBank(islandId, actorUuid, amount == null ? "" : amount)
            .thenApply(CoreBankCommandClient::bankMutation);
    }

    private static BankMutationView bankMutation(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        Map<?, ?> bank = SimpleJson.object(root.get("bank"));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String islandId = firstText(root, bank, "islandId");
        String balance = firstText(root, bank, "balance");
        String updatedAt = firstText(root, bank, "updatedAt");
        return new BankMutationView(accepted, SimpleJson.text(root.get("code")), islandId, balance, updatedAt);
    }

    private static String firstText(Map<?, ?> root, Map<?, ?> nested, String key) {
        String rootValue = SimpleJson.text(root.get(key));
        if (!rootValue.isBlank()) {
            return rootValue;
        }
        return SimpleJson.text(nested.get(key));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
