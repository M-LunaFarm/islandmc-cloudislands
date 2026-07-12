package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;

public final class IslandUpgradeService {
    public static final String PURCHASE_POLICY = "validate-rule-and-cost>withdraw-bank>compare-and-set-level>refund-on-conflict-or-write-failure";
    public static final String ECONOMY_ABSTRACTION_POLICY = "economy-bridge-or-island-bank-withdraw-with-compensating-refund-before-upgrade-level-commit";

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
        IslandUpgradeSnapshot currentSnapshot = repository.find(islandId, rule.upgradeKey()).orElse(null);
        int currentLevel = currentSnapshot == null ? 0 : currentSnapshot.level();
        if (currentLevel >= rule.maxLevel()) {
            return new UpgradePurchaseResult(false, "MAX_LEVEL", BigDecimal.ZERO, currentSnapshot);
        }
        BigDecimal cost = rule.costForNextLevel(currentLevel);
        if (cost.signum() < 0) {
            return new UpgradePurchaseResult(false, "INVALID_UPGRADE_COST", BigDecimal.ZERO, currentSnapshot);
        }
        if (cost.signum() > 0) {
            IslandBankRepository.BankChangeResult payment = bankRepository.withdraw(islandId, cost);
            if (!payment.accepted()) {
                return new UpgradePurchaseResult(false, payment.code(), cost, currentSnapshot);
            }
        }
        try {
            return repository.advanceLevel(islandId, rule.upgradeKey(), rule.type(), currentLevel, currentLevel + 1)
                .map(snapshot -> new UpgradePurchaseResult(true, "UPGRADED", cost, snapshot))
                .orElseGet(() -> failedAfterPayment(islandId, cost, "UPGRADE_CONFLICT", currentSnapshot));
        } catch (RuntimeException writeFailure) {
            return failedAfterPayment(islandId, cost, "UPGRADE_WRITE_FAILED", currentSnapshot);
        }
    }

    private UpgradePurchaseResult failedAfterPayment(UUID islandId, BigDecimal cost, String code, IslandUpgradeSnapshot currentSnapshot) {
        if (cost.signum() == 0) {
            return new UpgradePurchaseResult(false, code, cost, currentSnapshot);
        }
        try {
            bankRepository.deposit(islandId, cost);
            return new UpgradePurchaseResult(false, code + "_REFUNDED", cost, currentSnapshot);
        } catch (RuntimeException refundFailure) {
            return new UpgradePurchaseResult(false, code + "_REFUND_FAILED", cost, currentSnapshot);
        }
    }
}
