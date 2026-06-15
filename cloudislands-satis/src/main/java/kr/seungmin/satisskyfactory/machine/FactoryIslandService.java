package kr.seungmin.satisskyfactory.machine;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.hook.IslandRef;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class FactoryIslandService {
    private final SkyblockProvider skyblockHook;
    private final DatabaseService database;
    private final Map<UUID, FactoryIsland> cache = new ConcurrentHashMap<>();
    private DirtySaveService dirtySaves;
    private BooleanSupplier writesEnabled = () -> true;
    private boolean loaded;

    public FactoryIslandService(SkyblockProvider skyblockHook, DatabaseService database) {
        this.skyblockHook = skyblockHook;
        this.database = database;
    }

    public Optional<FactoryContext> context(Player player) {
        if (!loaded) {
            return Optional.empty();
        }
        Optional<IslandRef> island = skyblockHook.getIslandOf(player);
        if (island.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FactoryContext(island.get(), getOrCreate(island.get())));
    }

    public Optional<FactoryContext> existingContext(Player player) {
        if (!loaded) {
            return Optional.empty();
        }
        Optional<IslandRef> island = skyblockHook.getIslandOf(player);
        if (island.isEmpty()) {
            return Optional.empty();
        }
        return find(island.get().islandUuid()).map(factoryIsland -> new FactoryContext(island.get(), factoryIsland));
    }

    public void load() {
        cache.clear();
        for (FactoryIsland island : database.loadIslands()) {
            cache.put(island.islandUuid(), island);
        }
        loaded = true;
    }

    public FactoryIsland getOrCreate(IslandRef island) {
        if (!loaded) {
            FactoryIsland transientIsland = new FactoryIsland(island.islandUuid(), island.ownerUuid());
            transientIsland.ownerUuid(island.ownerUuid());
            return transientIsland;
        }
        FactoryIsland factoryIsland = cache.computeIfAbsent(island.islandUuid(), uuid -> {
            FactoryIsland loaded = database.findIsland(uuid).orElseGet(() -> new FactoryIsland(uuid, island.ownerUuid()));
            loaded.ownerUuid(island.ownerUuid());
            if (writesEnabled()) {
                database.saveIsland(loaded);
            }
            return loaded;
        });
        synchronizeOwner(factoryIsland, island.ownerUuid());
        return factoryIsland;
    }

    private void synchronizeOwner(FactoryIsland island, UUID ownerUuid) {
        if (island.ownerUuid().equals(ownerUuid)) {
            return;
        }
        island.ownerUuid(ownerUuid);
        save(island);
    }

    public Optional<FactoryIsland> find(UUID islandUuid) {
        if (!loaded) {
            return Optional.empty();
        }
        FactoryIsland cached = cache.get(islandUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<FactoryIsland> loaded = database.findIsland(islandUuid);
        loaded.ifPresent(island -> cache.put(island.islandUuid(), island));
        return loaded;
    }

    public Collection<FactoryIsland> cached() {
        if (!loaded) {
            return java.util.List.of();
        }
        return new ArrayList<>(cache.values());
    }

    public void save(FactoryIsland island) {
        if (!loaded) {
            return;
        }
        cache.put(island.islandUuid(), island);
        if (dirtySaves != null) {
            dirtySaves.markIsland(island);
            return;
        }
        if (!writesEnabled()) {
            return;
        }
        database.saveIsland(island);
    }

    public void forget(UUID islandUuid) {
        cache.remove(islandUuid);
        if (dirtySaves != null) {
            dirtySaves.forgetIsland(islandUuid);
        }
    }

    public void clear() {
        cache.clear();
        loaded = false;
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    private boolean writesEnabled() {
        try {
            return writesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
