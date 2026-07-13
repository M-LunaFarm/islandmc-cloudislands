package kr.lunaf.cloudislands.paper.integration.stacker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class StackAmountService {
    private static final List<String> SUPPORTED_PLUGINS = List.of("RoseStacker", "WildStacker", "AdvancedSpawners");

    private final List<Adapter> adapters;
    private final Map<String, Adapter> adaptersByPlugin;

    StackAmountService(List<Adapter> adapters) {
        List<Adapter> safeAdapters = adapters == null ? List.of() : adapters.stream()
            .filter(adapter -> adapter != null && adapter.pluginName() != null && !adapter.pluginName().isBlank())
            .toList();
        this.adapters = List.copyOf(safeAdapters);
        LinkedHashMap<String, Adapter> indexed = new LinkedHashMap<>();
        safeAdapters.forEach(adapter -> indexed.put(adapter.pluginName(), adapter));
        this.adaptersByPlugin = Map.copyOf(indexed);
    }

    public static StackAmountService discover(Server server) {
        List<Adapter> adapters = new ArrayList<>();
        if (enabled(server, "RoseStacker")) {
            Adapter adapter = roseStackerAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "WildStacker")) {
            Adapter adapter = wildStackerAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        if (enabled(server, "AdvancedSpawners")) {
            Adapter adapter = advancedSpawnersAdapter();
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        return new StackAmountService(adapters);
    }

    public static StackAmountService physicalOnly() {
        return new StackAmountService(List.of());
    }

    public StackSnapshot snapshot(World world, int minX, int maxX, int minZ, int maxZ) {
        Bounds bounds = new Bounds(world == null ? null : world.getUID(), minX, maxX, minZ, maxZ);
        MutableSnapshot merged = new MutableSnapshot(bounds);
        List<ToLongFunction<Block>> directResolvers = new ArrayList<>();
        for (Adapter adapter : adapters) {
            try {
                SnapshotData data = adapter.snapshotResolver() == null
                    ? SnapshotData.empty()
                    : adapter.snapshotResolver().apply(bounds);
                if (data != null) {
                    merged.merge(data);
                }
                if (adapter.directBlockAmount() != null) {
                    directResolvers.add(adapter.directBlockAmount());
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Optional vendor failure must never prevent vanilla reconciliation.
            }
        }
        return merged.freeze(directResolvers);
    }

    public boolean supports(String pluginName) {
        return pluginName != null && adaptersByPlugin.containsKey(pluginName);
    }

    public long entityAmount(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return 1L;
        }
        long amount = 1L;
        for (Adapter adapter : adapters) {
            if (adapter.directEntityAmount() == null) {
                continue;
            }
            try {
                amount = Math.max(amount, Math.max(1L, adapter.directEntityAmount().applyAsLong(livingEntity)));
            } catch (RuntimeException | LinkageError ignored) {
                // Optional vendor failure falls back to one physical entity.
            }
        }
        return amount;
    }

    public long spawnerSpawnAmount(CreatureSpawner spawner) {
        if (spawner == null) {
            return 1L;
        }
        long amount = 1L;
        for (Adapter adapter : adapters) {
            if (adapter.spawnerSpawnAmount() == null) {
                continue;
            }
            try {
                amount = Math.max(amount, Math.max(1L, adapter.spawnerSpawnAmount().applyAsLong(spawner)));
            } catch (RuntimeException | LinkageError ignored) {
                // Optional vendor failure falls back to one physical spawn.
            }
        }
        return amount;
    }

    public Map<String, String> runtimeDetails(String pluginName) {
        Adapter adapter = adaptersByPlugin.get(pluginName);
        return Map.of(
            "adapter", adapter == null ? "unavailable" : adapter.description(),
            "blockAmounts", Boolean.toString(adapter != null),
            "entityAmounts", Boolean.toString(adapter != null && adapter.entityAmounts()),
            "snapshotPolicy", adapter != null && adapter.snapshotResolver() != null ? "loaded-stack-snapshot" : "spawner-direct-lookup",
            "consumers", "island-level-rescan,worth,level,ranking,reconciled-block-limits"
        );
    }

    public static List<String> supportedPlugins() {
        return SUPPORTED_PLUGINS;
    }

    private static Adapter roseStackerAdapter() {
        try {
            Class<?> apiClass = Class.forName("dev.rosewood.rosestacker.api.RoseStackerAPI");
            Method getInstance = apiClass.getMethod("getInstance");
            Method getBlocks = apiClass.getMethod("getStackedBlocks");
            Method getSpawners = apiClass.getMethod("getStackedSpawners");
            Method getEntities = apiClass.getMethod("getStackedEntities");
            Method getStackedEntity = apiClass.getMethod("getStackedEntity", LivingEntity.class);
            ToLongFunction<LivingEntity> directEntity = entity -> {
                try {
                    return stackSize(getStackedEntity.invoke(getInstance.invoke(null), entity), "getStackSize");
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return 1L;
                }
            };
            Function<Bounds, SnapshotData> snapshot = bounds -> {
                try {
                    Object api = getInstance.invoke(null);
                    MutableSnapshot data = new MutableSnapshot(bounds);
                    collectRoseBlocks(data, getBlocks.invoke(api));
                    collectRoseBlocks(data, getSpawners.invoke(api));
                    collectRoseEntities(data, getEntities.invoke(api));
                    return data.data();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return SnapshotData.empty();
                }
            };
            return new Adapter("RoseStacker", snapshot, null, directEntity, null, true, "rosestacker-loaded-stack-api");
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Adapter wildStackerAdapter() {
        try {
            Class<?> apiClass = Class.forName("com.bgsoftware.wildstacker.api.WildStackerAPI");
            Method getPlugin = apiClass.getMethod("getWildStacker");
            Object plugin = getPlugin.invoke(null);
            if (plugin == null) {
                return null;
            }
            Method getManager = plugin.getClass().getMethod("getSystemManager");
            Object manager = getManager.invoke(plugin);
            if (manager == null) {
                return null;
            }
            Method getBarrels = manager.getClass().getMethod("getStackedBarrels");
            Method getSpawners = manager.getClass().getMethod("getStackedSpawners");
            Method getEntities = manager.getClass().getMethod("getStackedEntities");
            Method getEntityAmount = apiClass.getMethod("getEntityAmount", LivingEntity.class);
            Method getSpawnersAmount = apiClass.getMethod("getSpawnersAmount", CreatureSpawner.class);
            ToLongFunction<LivingEntity> directEntity = entity -> invokePositive(getEntityAmount, null, entity);
            ToLongFunction<CreatureSpawner> spawnerAmount = spawner -> invokePositive(getSpawnersAmount, null, spawner);
            Function<Bounds, SnapshotData> snapshot = bounds -> {
                try {
                    MutableSnapshot data = new MutableSnapshot(bounds);
                    collectWildBlocks(data, getBarrels.invoke(manager), true);
                    collectWildBlocks(data, getSpawners.invoke(manager), false);
                    collectWildEntities(data, getEntities.invoke(manager));
                    return data.data();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return SnapshotData.empty();
                }
            };
            return new Adapter("WildStacker", snapshot, null, directEntity, spawnerAmount, true, "wildstacker-loaded-stack-api");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Adapter advancedSpawnersAdapter() {
        try {
            Class<?> apiClass = Class.forName("gcspawners.ASAPI");
            Method getAmount = apiClass.getMethod("getSpawnerAmount", Location.class);
            ToLongFunction<Block> direct = block -> {
                if (block == null || block.getType() != Material.SPAWNER) {
                    return 1L;
                }
                try {
                    return positiveAmount(getAmount.invoke(null, block.getLocation()));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return 1L;
                }
            };
            return new Adapter("AdvancedSpawners", null, direct, null, null, false, "advancedspawners-spawner-api");
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void collectRoseBlocks(MutableSnapshot snapshot, Object mapValue) {
        if (!(mapValue instanceof Map<?, ?> stacks)) {
            return;
        }
        for (Map.Entry<?, ?> entry : stacks.entrySet()) {
            try {
                if (entry.getKey() instanceof Block block) {
                    snapshot.block(block, stackSize(entry.getValue(), "getStackSize"), "");
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Skip only the malformed vendor entry.
            }
        }
    }

    private static void collectRoseEntities(MutableSnapshot snapshot, Object mapValue) {
        if (!(mapValue instanceof Map<?, ?> stacks)) {
            return;
        }
        for (Object stack : stacks.values()) {
            try {
                Object entity = invoke(stack, "getEntity");
                if (entity instanceof Entity bukkitEntity) {
                    snapshot.entity(bukkitEntity, stackSize(stack, "getStackSize"));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Skip only the malformed vendor entry.
            }
        }
    }

    private static void collectWildBlocks(MutableSnapshot snapshot, Object listValue, boolean barrel) {
        if (!(listValue instanceof Iterable<?> stacks)) {
            return;
        }
        for (Object stack : stacks) {
            try {
                Object location = invoke(stack, "getLocation");
                if (!(location instanceof Location bukkitLocation)) {
                    continue;
                }
                String keyOverride = "";
                if (barrel) {
                    Object material = invoke(stack, "getType");
                    if (material instanceof Material bukkitMaterial) {
                        keyOverride = bukkitMaterial.getKey().toString();
                    }
                }
                snapshot.block(bukkitLocation, stackSize(stack, "getStackAmount"), keyOverride);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Skip only the malformed vendor entry.
            }
        }
    }

    private static void collectWildEntities(MutableSnapshot snapshot, Object listValue) {
        if (!(listValue instanceof Iterable<?> stacks)) {
            return;
        }
        for (Object stack : stacks) {
            try {
                Object uniqueId = invoke(stack, "getUniqueId");
                Object location = invoke(stack, "getLocation");
                if (uniqueId instanceof UUID entityId && location instanceof Location bukkitLocation) {
                    snapshot.entity(entityId, bukkitLocation, stackSize(stack, "getStackAmount"));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Skip only the malformed vendor entry.
            }
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return target == null ? null : target.getClass().getMethod(methodName).invoke(target);
    }

    private static long stackSize(Object stack, String methodName) throws ReflectiveOperationException {
        return positiveAmount(invoke(stack, methodName));
    }

    private static long positiveAmount(Object amount) {
        return amount instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
    }

    private static long invokePositive(Method method, Object target, Object argument) {
        try {
            return positiveAmount(method.invoke(target, argument));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 1L;
        }
    }

    private static boolean enabled(Server server, String pluginName) {
        try {
            return server != null && server.getPluginManager().isPluginEnabled(pluginName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static final class StackSnapshot {
        private final Map<BlockPosition, Long> blockAmounts;
        private final Map<BlockPosition, String> blockKeyOverrides;
        private final Map<UUID, Long> entityAmounts;
        private final List<ToLongFunction<Block>> directBlockResolvers;

        private StackSnapshot(
            Map<BlockPosition, Long> blockAmounts,
            Map<BlockPosition, String> blockKeyOverrides,
            Map<UUID, Long> entityAmounts,
            List<ToLongFunction<Block>> directBlockResolvers
        ) {
            this.blockAmounts = Map.copyOf(blockAmounts);
            this.blockKeyOverrides = Map.copyOf(blockKeyOverrides);
            this.entityAmounts = Map.copyOf(entityAmounts);
            this.directBlockResolvers = List.copyOf(directBlockResolvers);
        }

        public long blockAmount(Block block) {
            long amount = blockAmounts.getOrDefault(BlockPosition.of(block), 1L);
            for (ToLongFunction<Block> resolver : directBlockResolvers) {
                try {
                    amount = Math.max(amount, Math.max(1L, resolver.applyAsLong(block)));
                } catch (RuntimeException | LinkageError ignored) {
                    // Keep the already resolved amount.
                }
            }
            return amount;
        }

        public long entityAmount(Entity entity) {
            return entity == null ? 1L : entityAmounts.getOrDefault(entity.getUniqueId(), 1L);
        }

        public String blockKeyOverride(Block block) {
            return blockKeyOverrides.getOrDefault(BlockPosition.of(block), "");
        }
    }

    record Adapter(
        String pluginName,
        Function<Bounds, SnapshotData> snapshotResolver,
        ToLongFunction<Block> directBlockAmount,
        ToLongFunction<LivingEntity> directEntityAmount,
        ToLongFunction<CreatureSpawner> spawnerSpawnAmount,
        boolean entityAmounts,
        String description
    ) {
    }

    record Bounds(UUID worldId, int minX, int maxX, int minZ, int maxZ) {
        boolean contains(Location location) {
            return location != null
                && location.getWorld() != null
                && Objects.equals(worldId, location.getWorld().getUID())
                && location.getBlockX() >= minX
                && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ
                && location.getBlockZ() <= maxZ;
        }
    }

    record SnapshotData(
        Map<BlockPosition, Long> blockAmounts,
        Map<BlockPosition, String> blockKeyOverrides,
        Map<UUID, Long> entityAmounts
    ) {
        static SnapshotData empty() {
            return new SnapshotData(Map.of(), Map.of(), Map.of());
        }
    }

    record BlockPosition(UUID worldId, int x, int y, int z) {
        static BlockPosition of(Block block) {
            return block == null || block.getWorld() == null
                ? new BlockPosition(null, 0, 0, 0)
                : new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        static BlockPosition of(Location location) {
            return location == null || location.getWorld() == null
                ? new BlockPosition(null, 0, 0, 0)
                : new BlockPosition(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private static final class MutableSnapshot {
        private final Bounds bounds;
        private final Map<BlockPosition, Long> blockAmounts = new HashMap<>();
        private final Map<BlockPosition, String> blockKeyOverrides = new HashMap<>();
        private final Map<UUID, Long> entityAmounts = new HashMap<>();

        private MutableSnapshot(Bounds bounds) {
            this.bounds = bounds;
        }

        private void block(Block block, long amount, String keyOverride) {
            if (block != null) {
                block(block.getLocation(), amount, keyOverride);
            }
        }

        private void block(Location location, long amount, String keyOverride) {
            if (!bounds.contains(location)) {
                return;
            }
            BlockPosition position = BlockPosition.of(location);
            blockAmounts.merge(position, Math.max(1L, amount), Math::max);
            if (keyOverride != null && !keyOverride.isBlank()) {
                blockKeyOverrides.putIfAbsent(position, keyOverride.toLowerCase(Locale.ROOT));
            }
        }

        private void entity(Entity entity, long amount) {
            if (entity != null) {
                entity(entity.getUniqueId(), entity.getLocation(), amount);
            }
        }

        private void entity(UUID entityId, Location location, long amount) {
            if (entityId != null && bounds.contains(location)) {
                entityAmounts.merge(entityId, Math.max(1L, amount), Math::max);
            }
        }

        private void merge(SnapshotData data) {
            data.blockAmounts().forEach((position, amount) -> blockAmounts.merge(position, amount, Math::max));
            data.blockKeyOverrides().forEach(blockKeyOverrides::putIfAbsent);
            data.entityAmounts().forEach((entityId, amount) -> entityAmounts.merge(entityId, amount, Math::max));
        }

        private SnapshotData data() {
            return new SnapshotData(Map.copyOf(blockAmounts), Map.copyOf(blockKeyOverrides), Map.copyOf(entityAmounts));
        }

        private StackSnapshot freeze(List<ToLongFunction<Block>> directResolvers) {
            return new StackSnapshot(blockAmounts, blockKeyOverrides, entityAmounts, directResolvers);
        }
    }
}
