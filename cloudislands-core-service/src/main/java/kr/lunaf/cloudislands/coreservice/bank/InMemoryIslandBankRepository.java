package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public final class InMemoryIslandBankRepository implements IslandBankRepository {
    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> updatedAt = new ConcurrentHashMap<>();

    @Override
    public IslandBankSnapshot balance(UUID islandId) {
        return snapshot(islandId);
    }

    @Override
    public synchronized IslandBankSnapshot deposit(UUID islandId, BigDecimal amount) {
        if (!IslandBankRepository.positiveAmount(amount)) {
            return snapshot(islandId);
        }
        balances.merge(islandId, amount, BigDecimal::add);
        updatedAt.put(islandId, Instant.now());
        return snapshot(islandId);
    }

    @Override
    public synchronized BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        if (!IslandBankRepository.positiveAmount(amount)) {
            return new BankChangeResult(false, "INVALID_AMOUNT", snapshot(islandId));
        }
        BigDecimal current = balances.getOrDefault(islandId, BigDecimal.ZERO);
        BigDecimal limit = IslandBankRepository.effectiveMaxBalance(maxBalance);
        if (limit != null && current.add(amount).compareTo(limit) > 0) {
            return new BankChangeResult(false, "BANK_LIMIT", snapshot(islandId));
        }
        balances.put(islandId, current.add(amount));
        updatedAt.put(islandId, Instant.now());
        return new BankChangeResult(true, "DEPOSITED", snapshot(islandId));
    }

    @Override
    public synchronized BankChangeResult withdraw(UUID islandId, BigDecimal amount) {
        if (!IslandBankRepository.positiveAmount(amount)) {
            return new BankChangeResult(false, "INVALID_AMOUNT", snapshot(islandId));
        }
        BigDecimal current = balances.getOrDefault(islandId, BigDecimal.ZERO);
        if (current.compareTo(amount) < 0) {
            return new BankChangeResult(false, "INSUFFICIENT_FUNDS", snapshot(islandId));
        }
        balances.put(islandId, current.subtract(amount));
        updatedAt.put(islandId, Instant.now());
        return new BankChangeResult(true, "WITHDRAWN", snapshot(islandId));
    }

    private IslandBankSnapshot snapshot(UUID islandId) {
        return new IslandBankSnapshot(islandId, balances.getOrDefault(islandId, BigDecimal.ZERO).toPlainString(), updatedAt.getOrDefault(islandId, Instant.EPOCH));
    }
}
