package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface IslandBankRepository {
    IslandBankSnapshot balance(UUID islandId);
    IslandBankSnapshot deposit(UUID islandId, BigDecimal amount);
    default BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        if (!positiveAmount(amount)) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        IslandBankSnapshot current = balance(islandId);
        BigDecimal limit = effectiveMaxBalance(maxBalance);
        if (limit != null && new BigDecimal(current.balance()).add(amount).compareTo(limit) > 0) {
            return new BankChangeResult(false, "BANK_LIMIT", current);
        }
        return new BankChangeResult(true, "DEPOSITED", deposit(islandId, amount));
    }
    BankChangeResult withdraw(UUID islandId, BigDecimal amount);

    static boolean positiveAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    static BigDecimal effectiveMaxBalance(BigDecimal maxBalance) {
        return maxBalance == null || maxBalance.signum() < 0 ? null : maxBalance;
    }

    record BankChangeResult(boolean accepted, String code, IslandBankSnapshot snapshot) {}
}
