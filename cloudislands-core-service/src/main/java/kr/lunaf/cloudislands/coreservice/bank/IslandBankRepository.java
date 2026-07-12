package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface IslandBankRepository {
    BigDecimal MAX_STORABLE_BALANCE = new BigDecimal("999999999999999999.99");
    IslandBankSnapshot balance(UUID islandId);
    IslandBankSnapshot deposit(UUID islandId, BigDecimal amount);
    default BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        BigDecimal normalized = normalizeAmount(amount);
        if (normalized == null) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        IslandBankSnapshot current = balance(islandId);
        BigDecimal limit = effectiveMaxBalance(maxBalance);
        if (new BigDecimal(current.balance()).add(normalized).compareTo(limit) > 0) {
            return new BankChangeResult(false, "BANK_LIMIT", current);
        }
        return new BankChangeResult(true, "DEPOSITED", deposit(islandId, normalized));
    }
    BankChangeResult withdraw(UUID islandId, BigDecimal amount);

    static boolean positiveAmount(BigDecimal amount) {
        return normalizeAmount(amount) != null;
    }

    static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    static BigDecimal effectiveMaxBalance(BigDecimal maxBalance) {
        if (maxBalance == null || maxBalance.signum() < 0) {
            return MAX_STORABLE_BALANCE;
        }
        return maxBalance.min(MAX_STORABLE_BALANCE).setScale(2, RoundingMode.DOWN);
    }

    record BankChangeResult(boolean accepted, String code, IslandBankSnapshot snapshot) {}
}
