package kr.lunaf.cloudislands.coreservice.profile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
        Optional<PlayerIslandProfile> cached = cachedProfile(playerUuid);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(delegate.find(playerUuid));
    }

    @Override
    public Optional<PlayerIslandProfile> findByLastName(String lastName) {
        Optional<PlayerIslandProfile> cached = cachedProfileByName(lastName);
        if (cached.isPresent()) {
            return cached;
        }
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
            redis.command("SET", RedisKeys.playerProfile(profile.playerUuid()), profileJson(profile));
            if (profile.lastName() != null && !profile.lastName().isBlank()) {
                redis.command("SET", RedisKeys.playerNameProfile(profile.lastName()), profile.playerUuid().toString());
            }
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

    private Optional<PlayerIslandProfile> cachedProfile(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.playerProfile(playerUuid));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(profileFromJson(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private Optional<PlayerIslandProfile> cachedProfileByName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return Optional.empty();
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.playerNameProfile(lastName));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            Optional<PlayerIslandProfile> profile = cachedProfile(UUID.fromString(value));
            if (profile.isPresent() && profile.get().lastName().equalsIgnoreCase(lastName)) {
                return profile;
            }
            return Optional.empty();
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String profileJson(PlayerIslandProfile profile) {
        return profile.playerUuid()
            + "|" + encodeText(profile.lastName())
            + "|" + profile.primaryIslandId().map(UUID::toString).orElse("")
            + "|" + profile.lastSeenAt();
    }

    private static PlayerIslandProfile profileFromJson(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("invalid cached player profile");
        }
        return new PlayerIslandProfile(
            UUID.fromString(parts[0]),
            decodeText(parts[1]),
            parts[2].isBlank() ? Optional.empty() : Optional.of(UUID.fromString(parts[2])),
            instant(parts[3])
        );
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encodedBase64) {
        return new String(Base64.getUrlDecoder().decode(encodedBase64), StandardCharsets.UTF_8);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
