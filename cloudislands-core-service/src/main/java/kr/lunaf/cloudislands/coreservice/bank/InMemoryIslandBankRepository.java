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
        return deposit(islandId, amount, IslandBankRepository.MAX_STORABLE_BALANCE).snapshot();
    }

    @Override
    public synchronized BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        BigDecimal normalized = IslandBankRepository.normalizeAmount(amount);
        if (normalized == null) {
            return new BankChangeResult(false, "INVALID_AMOUNT", snapshot(islandId));
        }
        BigDecimal current = balances.getOrDefault(islandId, BigDecimal.ZERO.setScale(2));
        BigDecimal limit = IslandBankRepository.effectiveMaxBalance(maxBalance);
        if (current.add(normalized).compareTo(limit) > 0) {
            return new BankChangeResult(false, "BANK_LIMIT", snapshot(islandId));
        }
        balances.put(islandId, current.add(normalized));
        updatedAt.put(islandId, Instant.now());
        return new BankChangeResult(true, "DEPOSITED", snapshot(islandId));
    }

    @Override
    public synchronized BankChangeResult withdraw(UUID islandId, BigDecimal amount) {
        BigDecimal normalized = IslandBankRepository.normalizeAmount(amount);
        if (normalized == null) {
            return new BankChangeResult(false, "INVALID_AMOUNT", snapshot(islandId));
        }
        BigDecimal current = balances.getOrDefault(islandId, BigDecimal.ZERO.setScale(2));
        if (current.compareTo(normalized) < 0) {
            return new BankChangeResult(false, "INSUFFICIENT_FUNDS", snapshot(islandId));
        }
        balances.put(islandId, current.subtract(normalized));
        updatedAt.put(islandId, Instant.now());
        return new BankChangeResult(true, "WITHDRAWN", snapshot(islandId));
    }

    private IslandBankSnapshot snapshot(UUID islandId) {
        return new IslandBankSnapshot(islandId, balances.getOrDefault(islandId, BigDecimal.ZERO.setScale(2)).toPlainString(), updatedAt.getOrDefault(islandId, Instant.EPOCH));
    }
}
