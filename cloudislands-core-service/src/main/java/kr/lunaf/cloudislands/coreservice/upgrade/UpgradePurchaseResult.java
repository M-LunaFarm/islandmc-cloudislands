package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.Map;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;

public record UpgradePurchaseResult(boolean accepted, String code, BigDecimal cost, IslandUpgradeSnapshot snapshot, Map<String, Long> itemCosts) {
    public UpgradePurchaseResult(boolean accepted, String code, BigDecimal cost, IslandUpgradeSnapshot snapshot) {
        this(accepted, code, cost, snapshot, Map.of());
    }

    public UpgradePurchaseResult {
        itemCosts = itemCosts == null ? Map.of() : Map.copyOf(itemCosts);
    }
}
