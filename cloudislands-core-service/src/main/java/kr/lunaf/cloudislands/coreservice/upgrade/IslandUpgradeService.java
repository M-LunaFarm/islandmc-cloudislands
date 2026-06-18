package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;

public final class IslandUpgradeService {
    public static final String PURCHASE_POLICY = "validate-rule-max-level-cost-withdraw-bank-before-upgrade-level-write";
    public static final String ECONOMY_ABSTRACTION_POLICY = "economy-bridge-or-island-bank-withdraw-before-upgrade-level-commit";

    private final IslandUpgradeRepository repository;
    private final IslandBankRepository bankRepository;
    private final UpgradePolicy policy;

    public IslandUpgradeService(IslandUpgradeRepository repository, IslandBankRepository bankRepository, UpgradePolicy policy) {
        this.repository = repository;
        this.bankRepository = bankRepository;
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
        if (cost.signum() < 0) {
            return new UpgradePurchaseResult(false, "INVALID_UPGRADE_COST", BigDecimal.ZERO, repository.find(islandId, rule.upgradeKey()).orElse(null));
        }
        if (cost.signum() > 0) {
            IslandBankRepository.BankChangeResult payment = bankRepository.withdraw(islandId, cost);
            if (!payment.accepted()) {
                return new UpgradePurchaseResult(false, payment.code(), cost, repository.find(islandId, rule.upgradeKey()).orElse(null));
            }
        }
        IslandUpgradeSnapshot snapshot = repository.setLevel(islandId, rule.upgradeKey(), rule.type(), currentLevel + 1);
        return new UpgradePurchaseResult(true, "UPGRADED", cost, snapshot);
    }
}
