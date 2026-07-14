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
            return new PlayerIslandProfile(playerUuid, lastName == null ? "" : lastName, current.primaryIslandId(), Instant.now(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName, String locale) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, lastName == null ? "" : lastName, current.primaryIslandId(), Instant.now(), locale, current.disbandsRemaining(), current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setLocale(UUID playerUuid, String locale) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), locale, current.disbandsRemaining(), current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setIslandFlyEnabled(UUID playerUuid, boolean enabled) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), enabled);
        });
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), Optional.of(islandId), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), Optional.empty(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile setDisbandsRemaining(UUID playerUuid, int value) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), value, current.islandFlyEnabled());
        });
    }

    @Override
    public PlayerIslandProfile addDisbandsRemaining(UUID playerUuid, int delta) {
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return new PlayerIslandProfile(playerUuid, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), saturatingNonNegativeAdd(current.disbandsRemaining(), delta), current.islandFlyEnabled());
        });
    }

    private static PlayerIslandProfile current(UUID playerUuid, PlayerIslandProfile stored) {
        return stored == null ? new PlayerIslandProfile(playerUuid, "", Optional.empty(), Instant.EPOCH) : stored;
    }

    private static int saturatingNonNegativeAdd(int current, int delta) {
        try {
            return Math.max(0, Math.addExact(current, delta));
        } catch (ArithmeticException overflow) {
            return delta > 0 ? Integer.MAX_VALUE : 0;
        }
    }
}
