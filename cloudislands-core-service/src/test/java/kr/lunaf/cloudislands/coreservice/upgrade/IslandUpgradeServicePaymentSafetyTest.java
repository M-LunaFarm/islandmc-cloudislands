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
        assertEquals("100", bank.balance(ISLAND).balance());
    }

    @Test
    void refundsBankWhenUpgradeWriteThrows() {
        InMemoryIslandBankRepository bank = fundedBank();
        IslandUpgradeService service = new IslandUpgradeService(new RejectingAdvanceRepository(true), bank, policy());

        UpgradePurchaseResult result = service.purchase(ISLAND, "size");

        assertFalse(result.accepted());
        assertEquals("UPGRADE_WRITE_FAILED_REFUNDED", result.code());
        assertEquals("100", bank.balance(ISLAND).balance());
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
}
