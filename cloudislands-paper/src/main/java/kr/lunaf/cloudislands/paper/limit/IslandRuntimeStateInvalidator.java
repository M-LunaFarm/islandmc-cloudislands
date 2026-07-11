package kr.lunaf.cloudislands.paper.limit;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class IslandRuntimeStateInvalidator {
    private final List<Consumer<UUID>> invalidators;

    public IslandRuntimeStateInvalidator(List<Consumer<UUID>> invalidators) {
        this.invalidators = List.copyOf(invalidators);
    }

    public void invalidate(UUID islandId) {
        invalidators.forEach(invalidator -> invalidator.accept(islandId));
    }
}
