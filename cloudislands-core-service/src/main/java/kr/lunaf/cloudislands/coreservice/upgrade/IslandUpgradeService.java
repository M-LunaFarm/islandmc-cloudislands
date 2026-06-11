package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;

public final class IslandUpgradeService {
    private final IslandUpgradeRepository repository;
    private final UpgradePolicy policy;

    public IslandUpgradeService(IslandUpgradeRepository repository, UpgradePolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public UpgradePurchaseResult purchase(UUID islandId, String upgradeKey) {
        UpgradeRule rule = policy.rule(upgradeKey);
        if (rule == null) {
            return new UpgradePurchaseResult(false, "UNKNOWN_UPGRADE", BigDecimal.ZERO, null);
        }
        int currentLevel = repository.find(islandId, rule.upgradeKey()).map(IslandUpgradeSnapshot::level).orElse(0);
        if (currentLevel >= rule.maxLevel()) {
            return new UpgradePurchaseResult(false, "MAX_LEVEL", BigDecimal.ZERO, repository.find(islandId, rule.upgradeKey()).orElse(null));
        }
        BigDecimal cost = rule.costForNextLevel(currentLevel);
        IslandUpgradeSnapshot snapshot = repository.setLevel(islandId, rule.upgradeKey(), rule.type(), currentLevel + 1);
        return new UpgradePurchaseResult(true, "UPGRADED", cost, snapshot);
    }
}
