package kr.lunaf.cloudislands.api.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class EconomyBridgeTest {
    @Test
    void defaultProviderStateKeepsExistingBridgesCompatible() {
        EconomyBridge bridge = new EconomyBridge() {
            @Override
            public CompletableFuture<Boolean> withdraw(UUID playerUuid, BigDecimal amount, String reason) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<BigDecimal> balance(UUID playerUuid) {
                return CompletableFuture.completedFuture(BigDecimal.ZERO);
            }
        };

        assertEquals(EconomyProviderState.ACTIVE, bridge.providerState());
    }
}
