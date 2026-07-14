package kr.lunaf.cloudislands.coreservice.profile;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public final class InMemoryPlayerProfileRepository implements PlayerProfileRepository {
    private final Map<UUID, PlayerIslandProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public PlayerIslandProfile find(UUID playerUuid) {
        return profiles.getOrDefault(playerUuid, new PlayerIslandProfile(playerUuid, "", Optional.empty(), Instant.EPOCH));
    }

    @Override
    public Optional<PlayerIslandProfile> findByLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return Optional.empty();
        }
        return profiles.values().stream()
            .filter(profile -> profile.lastName().equalsIgnoreCase(lastName))
            .findFirst();
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, lastName == null ? "" : lastName, current.primaryIslandId(), Instant.now(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName, String locale) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, lastName == null ? "" : lastName, current.primaryIslandId(), Instant.now(), locale, current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setLocale(UUID playerUuid, String locale) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), locale, current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setIslandFlyEnabled(UUID playerUuid, boolean enabled) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), enabled, current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setWorldBorderEnabled(UUID playerUuid, boolean enabled) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), enabled, current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setBlocksStackerEnabled(UUID playerUuid, boolean enabled) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), enabled);
        });
    }

    @Override
    public PlayerIslandProfile setBorderColor(UUID playerUuid, String color) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(current.playerUuid(), current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled(), color);
        });
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), Optional.of(islandId), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), Optional.empty(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setDisbandsRemaining(UUID playerUuid, int value) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), value, current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public PlayerIslandProfile addDisbandsRemaining(UUID playerUuid, int delta) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), saturatingNonNegativeAdd(current.disbandsRemaining(), delta), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    private static PlayerIslandProfile current(UUID playerUuid, PlayerIslandProfile stored) {
        return stored == null ? new PlayerIslandProfile(playerUuid, "", Optional.empty(), Instant.EPOCH) : stored;
    }

    private static PlayerIslandProfile copy(PlayerIslandProfile current, String lastName, Optional<UUID> islandId, Instant lastSeenAt, String locale, int disbandsRemaining, boolean fly, boolean border, boolean blocks) {
        return new PlayerIslandProfile(current.playerUuid(), lastName, islandId, lastSeenAt, locale, disbandsRemaining, fly, border, blocks, current.borderColor());
    }

    private static int saturatingNonNegativeAdd(int current, int delta) {
        try {
            return Math.max(0, Math.addExact(current, delta));
        } catch (ArithmeticException overflow) {
            return delta > 0 ? Integer.MAX_VALUE : 0;
        }
    }
}
