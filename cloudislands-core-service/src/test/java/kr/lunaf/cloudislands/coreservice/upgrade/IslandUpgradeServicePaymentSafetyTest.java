package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.InMemoryIslandWarehouseRepository;
import org.junit.jupiter.api.Test;

class IslandUpgradeServicePaymentSafetyTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000941");

    @Test
    void refundsBankWhenConcurrentLevelAdvanceWins() {
        InMemoryIslandBankRepository bank = fundedBank();
        IslandUpgradeService service = new IslandUpgradeService(new RejectingAdvanceRepository(false), bank, policy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertFalse(result.accepted());
        assertEquals("UPGRADE_CONFLICT_REFUNDED", result.code());
        assertEquals("100.00", bank.balance(ISLAND).balance());
    }

    @Test
    void refundsBankWhenUpgradeWriteThrows() {
        InMemoryIslandBankRepository bank = fundedBank();
        IslandUpgradeService service = new IslandUpgradeService(new RejectingAdvanceRepository(true), bank, policy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertFalse(result.accepted());
        assertEquals("UPGRADE_WRITE_FAILED_REFUNDED", result.code());
        assertEquals("100.00", bank.balance(ISLAND).balance());
    }

    @Test
    void reportsRefundFailureWhenConcurrentDepositFillsBankCapacity() {
        FillingAfterWithdrawBank bank = new FillingAfterWithdrawBank();
        bank.deposit(ISLAND, new BigDecimal("100"));
        IslandUpgradeService service = new IslandUpgradeService(new RejectingAdvanceRepository(false), bank, policy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertFalse(result.accepted());
        assertEquals("UPGRADE_CONFLICT_REFUND_FAILED", result.code());
        assertEquals(IslandBankRepository.MAX_STORABLE_BALANCE.toPlainString(), bank.balance(ISLAND).balance());
    }

    @Test
    void compareAndSetAllowsOnlyOneInitialAdvance() {
        InMemoryIslandUpgradeRepository repository = new InMemoryIslandUpgradeRepository();

        Optional<IslandUpgradeSnapshot> first = repository.advanceLevel(ISLAND, "size", UpgradeType.ISLAND_SIZE, 0, 1);
        Optional<IslandUpgradeSnapshot> duplicate = repository.advanceLevel(ISLAND, "size", UpgradeType.ISLAND_SIZE, 0, 1);

        assertTrue(first.isPresent());
        assertTrue(duplicate.isEmpty());
        assertEquals(1, repository.find(ISLAND, "size").orElseThrow().level());
    }

    @Test
    void chargesBankAndMultipleWarehouseItemsTogether() {
        InMemoryIslandBankRepository bank = fundedBank();
        InMemoryIslandWarehouseRepository warehouse = fundedWarehouse(8, 3);
        InMemoryIslandUpgradeRepository upgrades = new InMemoryIslandUpgradeRepository();
        IslandUpgradeService service = new IslandUpgradeService(upgrades, bank, warehouse, itemPolicy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertTrue(result.accepted());
        assertEquals("90.00", bank.balance(ISLAND).balance());
        assertEquals(Map.of("minecraft:diamond", 4L, "minecraft:emerald", 1L), warehouseAmounts(warehouse));
    }

    @Test
    void refundsEarlierPricesWhenLaterItemIsInsufficient() {
        InMemoryIslandBankRepository bank = fundedBank();
        InMemoryIslandWarehouseRepository warehouse = fundedWarehouse(8, 1);
        IslandUpgradeService service = new IslandUpgradeService(new InMemoryIslandUpgradeRepository(), bank, warehouse, itemPolicy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertFalse(result.accepted());
        assertEquals("ITEM_PAYMENT_INSUFFICIENT_ITEMS_REFUNDED", result.code());
        assertEquals("100.00", bank.balance(ISLAND).balance());
        assertEquals(Map.of("minecraft:diamond", 8L, "minecraft:emerald", 1L), warehouseAmounts(warehouse));
    }

    private static InMemoryIslandBankRepository fundedBank() {
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();
        bank.deposit(ISLAND, new BigDecimal("100"));
        return bank;
    }

    private static UpgradePolicy policy() {
        return new UpgradePolicy(Map.of(
            "size", new UpgradeRule("size", UpgradeType.ISLAND_SIZE, 3, new BigDecimal("10"), BigDecimal.ONE)
        ));
    }

    private static UpgradePolicy itemPolicy() {
        UpgradeRule rule = new UpgradeRule(
            "size", UpgradeType.ISLAND_SIZE, 3, new BigDecimal("10"), BigDecimal.ONE,
            Map.of(), Map.of(), Map.of(1, Map.of("DIAMOND", 4L, "minecraft:emerald", 2L))
        );
        return new UpgradePolicy(Map.of("size", rule));
    }

    private static InMemoryIslandWarehouseRepository fundedWarehouse(long diamonds, long emeralds) {
        InMemoryIslandWarehouseRepository warehouse = new InMemoryIslandWarehouseRepository();
        warehouse.deposit(ISLAND, "DIAMOND", diamonds);
        warehouse.deposit(ISLAND, "EMERALD", emeralds);
        return warehouse;
    }

    private static Map<String, Long> warehouseAmounts(InMemoryIslandWarehouseRepository warehouse) {
        return warehouse.list(ISLAND, 100).stream().collect(java.util.stream.Collectors.toMap(
            kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot::materialKey,
            kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot::amount
        ));
    }

    private static final class RejectingAdvanceRepository implements IslandUpgradeRepository {
        private final boolean throwOnAdvance;

        private RejectingAdvanceRepository(boolean throwOnAdvance) {
            this.throwOnAdvance = throwOnAdvance;
        }

        @Override
        public Optional<IslandUpgradeSnapshot> find(UUID islandId, String upgradeKey) {
            return Optional.empty();
        }

        @Override
        public List<IslandUpgradeSnapshot> list(UUID islandId) {
            return List.of();
        }

        @Override
        public IslandUpgradeSnapshot setLevel(UUID islandId, String upgradeKey, UpgradeType type, int level) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IslandUpgradeSnapshot> advanceLevel(UUID islandId, String upgradeKey, UpgradeType type, int expectedLevel, int newLevel) {
            if (throwOnAdvance) {
                throw new IllegalStateException("simulated write failure");
            }
            return Optional.empty();
        }
    }

    private static final class FillingAfterWithdrawBank implements IslandBankRepository {
        private final InMemoryIslandBankRepository delegate = new InMemoryIslandBankRepository();

        @Override
        public kr.lunaf.cloudislands.api.model.IslandBankSnapshot balance(UUID islandId) {
            return delegate.balance(islandId);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandBankSnapshot deposit(UUID islandId, BigDecimal amount) {
            return delegate.deposit(islandId, amount);
        }

        @Override
        public BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
            return delegate.deposit(islandId, amount, maxBalance);
        }

        @Override
        public BankChangeResult withdraw(UUID islandId, BigDecimal amount) {
            BankChangeResult result = delegate.withdraw(islandId, amount);
            if (result.accepted()) {
                BigDecimal remainingCapacity = MAX_STORABLE_BALANCE.subtract(new BigDecimal(result.snapshot().balance()));
                delegate.deposit(islandId, remainingCapacity, MAX_STORABLE_BALANCE);
            }
            return result;
        }
    }
}
