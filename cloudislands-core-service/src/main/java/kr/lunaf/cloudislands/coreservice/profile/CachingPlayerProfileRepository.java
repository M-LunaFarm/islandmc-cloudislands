package kr.lunaf.cloudislands.coreservice.profile;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingPlayerProfileRepository implements PlayerProfileRepository {
    private final PlayerProfileRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingPlayerProfileRepository(PlayerProfileRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public PlayerIslandProfile find(UUID playerUuid) {
        return cache(delegate.find(playerUuid));
    }

    @Override
    public Optional<PlayerIslandProfile> findByLastName(String lastName) {
        Optional<PlayerIslandProfile> profile = delegate.findByLastName(lastName);
        profile.ifPresent(this::cache);
        return profile;
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName) {
        return cache(delegate.touch(playerUuid, lastName));
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        return cache(delegate.setPrimaryIsland(playerUuid, islandId));
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        return cache(delegate.clearPrimaryIsland(playerUuid));
    }

    public long failuresTotal() {
        return failures.get();
    }

    private PlayerIslandProfile cache(PlayerIslandProfile profile) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            if (profile.primaryIslandId().isPresent()) {
                redis.command("SET", RedisKeys.playerIsland(profile.playerUuid()), profile.primaryIslandId().get().toString());
            } else {
                redis.command("DEL", RedisKeys.playerIsland(profile.playerUuid()));
            }
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return profile;
    }
}
