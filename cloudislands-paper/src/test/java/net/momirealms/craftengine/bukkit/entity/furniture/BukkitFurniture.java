package net.momirealms.craftengine.bukkit.entity.furniture;

public final class BukkitFurniture {
    private final FurnitureDefinition config;

    public BukkitFurniture(String id) {
        this.config = new FurnitureDefinition(new Key(id));
    }

    public FurnitureDefinition config() {
        return config;
    }

    public record FurnitureDefinition(Key id) {
    }

    public record Key(String value) {
        public String asString() {
            return value;
        }
    }
}
