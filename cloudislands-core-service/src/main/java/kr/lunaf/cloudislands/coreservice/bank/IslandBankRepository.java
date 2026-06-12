package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface IslandBankRepository {
    IslandBankSnapshot balance(UUID islandId);
    IslandBankSnapshot deposit(UUID islandId, BigDecimal amount);
    default BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        if (amount.signum() <= 0) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        IslandBankSnapshot current = balance(islandId);
        if (maxBalance != null && new BigDecimal(current.balance()).add(amount).compareTo(maxBalance) > 0) {
            return new BankChangeResult(false, "BANK_LIMIT", current);
        }
        return new BankChangeResult(true, "DEPOSITED", deposit(islandId, amount));
    }
    BankChangeResult withdraw(UUID islandId, BigDecimal amount);

    record BankChangeResult(boolean accepted, String code, IslandBankSnapshot snapshot) {}
}
