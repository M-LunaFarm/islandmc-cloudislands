package kr.lunaf.cloudislands.coreservice.upgrade;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;

public final class IslandUpgradeService {
    public static final String PURCHASE_POLICY = "validate-rule-and-prices>withdraw-bank-and-warehouse-items>compare-and-set-level>refund-all-prices-on-failure";
    public static final String ECONOMY_ABSTRACTION_POLICY = "combined-bank-and-warehouse-item-prices-with-reverse-order-compensating-refunds";

    private final IslandUpgradeRepository repository;
    private final IslandBankRepository bankRepository;
    private final IslandWarehouseRepository warehouseRepository;
    private final UpgradePolicy policy;

    public IslandUpgradeService(IslandUpgradeRepository repository, IslandBankRepository bankRepository, UpgradePolicy policy) {
        this(repository, bankRepository, null, policy);
    }

    public IslandUpgradeService(IslandUpgradeRepository repository, IslandBankRepository bankRepository, IslandWarehouseRepository warehouseRepository, UpgradePolicy policy) {
        this.repository = repository;
        this.bankRepository = bankRepository;
        this.warehouseRepository = warehouseRepository;
        this.policy = policy;
    }

    public UpgradePurchaseResult purchase(UUID islandId, String upgradeKey) {
        UpgradeRule rule = policy.rule(upgradeKey);
        if (rule == null) {
            return new UpgradePurchaseResult(false, "UNKNOWN_UPGRADE", BigDecimal.ZERO, null, Map.of());
        }
        IslandUpgradeSnapshot currentSnapshot = repository.find(islandId, rule.upgradeKey()).orElse(null);
        int currentLevel = currentSnapshot == null ? 0 : currentSnapshot.level();
        if (currentLevel >= rule.maxLevel()) {
            return new UpgradePurchaseResult(false, "MAX_LEVEL", BigDecimal.ZERO, currentSnapshot, Map.of());
        }
        BigDecimal cost = rule.costForNextLevel(currentLevel);
        Map<String, Long> itemCosts = rule.itemCostsForNextLevel(currentLevel);
        if (cost.signum() < 0) {
            return new UpgradePurchaseResult(false, "INVALID_UPGRADE_COST", BigDecimal.ZERO, currentSnapshot, itemCosts);
        }
        if (!itemCosts.isEmpty() && warehouseRepository == null) {
            return new UpgradePurchaseResult(false, "ITEM_PRICE_UNAVAILABLE", cost, currentSnapshot, itemCosts);
        }
        if (cost.signum() > 0) {
            IslandBankRepository.BankChangeResult payment = bankRepository.withdraw(islandId, cost);
            if (!payment.accepted()) {
                return new UpgradePurchaseResult(false, payment.code(), cost, currentSnapshot, itemCosts);
            }
        }
        Map<String, Long> paidItems = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Long> itemCost : itemCosts.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                IslandWarehouseRepository.ChangeResult payment = warehouseRepository.withdraw(islandId, itemCost.getKey(), itemCost.getValue());
                if (!payment.accepted()) {
                    return failedAfterPayment(islandId, cost, itemCosts, "ITEM_PAYMENT_" + payment.code(), currentSnapshot, paidItems);
                }
                paidItems.put(itemCost.getKey(), itemCost.getValue());
            }
        } catch (RuntimeException itemPaymentFailure) {
            return failedAfterPayment(islandId, cost, itemCosts, "ITEM_PAYMENT_FAILED", currentSnapshot, paidItems);
        }
        try {
            return repository.advanceLevel(islandId, rule.upgradeKey(), rule.type(), currentLevel, currentLevel + 1)
                .map(snapshot -> new UpgradePurchaseResult(true, "UPGRADED", cost, snapshot, itemCosts))
                .orElseGet(() -> failedAfterPayment(islandId, cost, itemCosts, "UPGRADE_CONFLICT", currentSnapshot, paidItems));
        } catch (RuntimeException writeFailure) {
            return failedAfterPayment(islandId, cost, itemCosts, "UPGRADE_WRITE_FAILED", currentSnapshot, paidItems);
        }
    }

    private UpgradePurchaseResult failedAfterPayment(UUID islandId, BigDecimal cost, Map<String, Long> itemCosts, String code, IslandUpgradeSnapshot currentSnapshot, Map<String, Long> paidItems) {
        if (cost.signum() == 0 && paidItems.isEmpty()) {
            return new UpgradePurchaseResult(false, code, cost, currentSnapshot, itemCosts);
        }
        boolean refundFailed = false;
        try {
            for (Map.Entry<String, Long> paidItem : paidItems.entrySet().stream().toList().reversed()) {
                if (!warehouseRepository.deposit(islandId, paidItem.getKey(), paidItem.getValue()).accepted()) {
                    refundFailed = true;
                }
            }
        } catch (RuntimeException itemRefundFailure) {
            refundFailed = true;
        }
        if (cost.signum() > 0) {
            try {
                if (!bankRepository.deposit(islandId, cost, IslandBankRepository.MAX_STORABLE_BALANCE).accepted()) {
                    refundFailed = true;
                }
            } catch (RuntimeException bankRefundFailure) {
                refundFailed = true;
            }
        }
        return new UpgradePurchaseResult(false, code + (refundFailed ? "_REFUND_FAILED" : "_REFUNDED"), cost, currentSnapshot, itemCosts);
    }
}
