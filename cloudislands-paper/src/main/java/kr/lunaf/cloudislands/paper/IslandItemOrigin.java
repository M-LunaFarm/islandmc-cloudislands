package kr.lunaf.cloudislands.paper;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;

final class IslandItemOrigin {
    private static final NamespacedKey ORIGIN_ISLAND_KEY = new NamespacedKey("cloudislands", "origin-island");

    private IslandItemOrigin() {
    }

    static void mark(Item item, UUID islandId) {
        if (origin(item).isEmpty()) {
            item.getPersistentDataContainer().set(ORIGIN_ISLAND_KEY, PersistentDataType.STRING, islandId.toString());
        }
    }

    static Optional<UUID> origin(Item item) {
        return decode(item.getPersistentDataContainer().get(ORIGIN_ISLAND_KEY, PersistentDataType.STRING));
    }

    static Optional<UUID> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(encoded));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean compatible(Optional<UUID> source, Optional<UUID> target) {
        return source.equals(target);
    }

    static boolean destinationAllowed(UUID originIslandId, Optional<UUID> destinationIslandId) {
        return destinationIslandId.filter(originIslandId::equals).isPresent();
    }
}
