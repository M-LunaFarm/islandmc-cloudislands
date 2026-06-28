package kr.lunaf.cloudislands.api.economy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyBridge {
    default EconomyProviderState providerState() {
        return EconomyProviderState.ACTIVE;
    }

    CompletableFuture<Boolean> withdraw(UUID playerUuid, BigDecimal amount, String reason);
    CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason);
    CompletableFuture<BigDecimal> balance(UUID playerUuid);
}
