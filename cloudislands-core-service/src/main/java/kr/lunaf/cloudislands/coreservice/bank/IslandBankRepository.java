package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface IslandBankRepository {
    IslandBankSnapshot balance(UUID islandId);
    IslandBankSnapshot deposit(UUID islandId, BigDecimal amount);
    BankChangeResult withdraw(UUID islandId, BigDecimal amount);

    record BankChangeResult(boolean accepted, String code, IslandBankSnapshot snapshot) {}
}
