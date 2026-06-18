package kr.lunaf.cloudislands.paper.upgrade;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;

public final class UpgradePurchaseService {
    public static final String PAPER_ECONOMY_POLICY = "paper-agent-uses-economy-bridge-for-player-facing-upgrade-purchase";

    private final EconomyBridge economy;

    public UpgradePurchaseService(EconomyBridge economy) {
        this.economy = economy;
    }

    public CompletableFuture<Boolean> purchase(UUID playerUuid, UUID islandId, UpgradeCost cost) {
        return economy.withdraw(playerUuid, cost.cost(), "cloudislands upgrade " + islandId + " " + cost.upgradeKey())
            .thenApply(Boolean::booleanValue);
    }
}
