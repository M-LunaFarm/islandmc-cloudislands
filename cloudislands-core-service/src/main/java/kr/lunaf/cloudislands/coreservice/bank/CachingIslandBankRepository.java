package kr.lunaf.cloudislands.coreservice.bank;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandBankRepository implements IslandBankRepository {
    private final IslandBankRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandBankRepository(IslandBankRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public IslandBankSnapshot balance(UUID islandId) {
        Optional<IslandBankSnapshot> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(delegate.balance(islandId));
    }

    @Override
    public IslandBankSnapshot deposit(UUID islandId, BigDecimal amount) {
        return cache(delegate.deposit(islandId, amount));
    }

    @Override
    public BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        BankChangeResult result = delegate.deposit(islandId, amount, maxBalance);
        cache(result.snapshot());
        return result;
    }

    @Override
    public BankChangeResult withdraw(UUID islandId, BigDecimal amount) {
        BankChangeResult result = delegate.withdraw(islandId, amount);
        cache(result.snapshot());
        return result;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private IslandBankSnapshot cache(IslandBankSnapshot snapshot) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandBank(snapshot.islandId()), encode(snapshot));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return snapshot;
    }

    private Optional<IslandBankSnapshot> cached(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandBank(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3) {
                return Optional.empty();
            }
            return Optional.of(new IslandBankSnapshot(
                UUID.fromString(parts[0]),
                parts[1],
                instant(parts[2])
            ));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(IslandBankSnapshot snapshot) {
        return snapshot.islandId() + "|" + snapshot.balance() + "|" + snapshot.updatedAt();
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
