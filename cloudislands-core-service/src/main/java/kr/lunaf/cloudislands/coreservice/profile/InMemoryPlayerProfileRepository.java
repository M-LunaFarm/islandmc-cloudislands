package kr.lunaf.cloudislands.coreservice.profile;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public final class InMemoryPlayerProfileRepository implements PlayerProfileRepository {
    private final Map<UUID, PlayerIslandProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> primaryIslandSelectionRevisions = new ConcurrentHashMap<>();
    private final Map<PreferenceRevisionKey, Long> preferenceRevisions = new ConcurrentHashMap<>();

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
    public synchronized PlayerIslandProfile setIslandFlyEnabled(UUID playerUuid, boolean enabled) {
        String preferenceKey = "island-fly";
        long revision = reservePreferenceMutation(playerUuid, preferenceKey);
        return setIslandFlyEnabledIfPreferenceCurrent(playerUuid, enabled, preferenceKey, revision).orElseThrow();
    }

    @Override
    public synchronized long reservePreferenceMutation(UUID playerUuid, String preferenceKey) {
        PreferenceRevisionKey key = new PreferenceRevisionKey(playerUuid, normalizePreferenceKey(preferenceKey));
        return preferenceRevisions.merge(key, 1L, InMemoryPlayerProfileRepository::incrementRevision);
    }

    @Override
    public synchronized Optional<PlayerIslandProfile> setIslandFlyEnabledIfPreferenceCurrent(UUID playerUuid, boolean enabled, String preferenceKey, long preferenceRevision) {
        PreferenceRevisionKey key = new PreferenceRevisionKey(playerUuid, normalizePreferenceKey(preferenceKey));
        if (preferenceRevision <= 0L || preferenceRevisions.getOrDefault(key, 0L) != preferenceRevision) {
            return Optional.empty();
        }
        return Optional.of(profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), current.primaryIslandId(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), enabled, current.worldBorderEnabled(), current.blocksStackerEnabled());
        }));
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
    public synchronized PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        primaryIslandSelectionRevisions.merge(playerUuid, 1L, InMemoryPlayerProfileRepository::incrementRevision);
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), Optional.of(islandId), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public synchronized PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        primaryIslandSelectionRevisions.merge(playerUuid, 1L, InMemoryPlayerProfileRepository::incrementRevision);
        return profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), Optional.empty(), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
    }

    @Override
    public synchronized long reservePrimaryIslandSelection(UUID playerUuid) {
        return primaryIslandSelectionRevisions.merge(playerUuid, 1L, InMemoryPlayerProfileRepository::incrementRevision);
    }

    @Override
    public synchronized Optional<PlayerIslandProfile> setPrimaryIslandIfSelectionCurrent(UUID playerUuid, UUID islandId, long selectionRevision) {
        if (selectionRevision <= 0L || primaryIslandSelectionRevisions.getOrDefault(playerUuid, 0L) != selectionRevision) {
            return Optional.empty();
        }
        PlayerIslandProfile updated = profiles.compute(playerUuid, (_uuid, stored) -> {
            PlayerIslandProfile current = current(playerUuid, stored);
            return copy(current, current.lastName(), Optional.of(islandId), current.lastSeenAt(), current.locale(), current.disbandsRemaining(), current.islandFlyEnabled(), current.worldBorderEnabled(), current.blocksStackerEnabled());
        });
        return Optional.of(updated);
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

    private static long incrementRevision(long current, long ignored) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
    }

    private static String normalizePreferenceKey(String preferenceKey) {
        String normalized = preferenceKey == null ? "" : preferenceKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("invalid player preference key");
        }
        return normalized;
    }

    private record PreferenceRevisionKey(UUID playerUuid, String preferenceKey) {
        private PreferenceRevisionKey {
            if (playerUuid == null) {
                throw new IllegalArgumentException("playerUuid is required");
            }
        }
    }
}
