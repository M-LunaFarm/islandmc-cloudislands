package kr.lunaf.cloudislands.coreservice.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIslandBankRepositoryTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000831");

    @Test
    void exactDatabaseMaximumIsAcceptedAndNextCentIsRejected() {
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();

        assertTrue(bank.deposit(ISLAND, IslandBankRepository.MAX_STORABLE_BALANCE, null).accepted());
        IslandBankRepository.BankChangeResult rejected = bank.deposit(ISLAND, new BigDecimal("0.01"), null);

        assertFalse(rejected.accepted());
        assertEquals("BANK_LIMIT", rejected.code());
        assertEquals("999999999999999999.99", rejected.snapshot().balance());
        assertEquals(rejected.snapshot().balance(), bank.balance(ISLAND).balance());
    }

    @Test
    void rejectsSubCentAmountsInsteadOfSilentlyRoundingDatabaseWrites() {
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();

        assertNull(IslandBankRepository.normalizeAmount(new BigDecimal("1.001")));
        assertFalse(bank.deposit(ISLAND, new BigDecimal("1.001"), null).accepted());
        assertFalse(bank.withdraw(ISLAND, new BigDecimal("0.001")).accepted());
        assertEquals("0.00", bank.balance(ISLAND).balance());
    }

    @Test
    void configuredLimitCannotExceedPhysicalDatabaseCapacity() {
        assertEquals(
            IslandBankRepository.MAX_STORABLE_BALANCE,
            IslandBankRepository.effectiveMaxBalance(new BigDecimal("999999999999999999999999"))
        );
    }
}
