package kr.lunaf.cloudislands.paper.integration.stacker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class StackAmountServiceTest {
    private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000155");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000156");

    @Test
    void mergesLogicalAmountsWithoutDoubleCountingMultipleProviders() {
        StackAmountService.BlockPosition position = new StackAmountService.BlockPosition(WORLD_ID, 4, 70, 6);
        StackAmountService first = new StackAmountService(List.of(
            adapter("RoseStacker", new StackAmountService.SnapshotData(
                Map.of(position, 8L),
                Map.of(),
                Map.of(ENTITY_ID, 12L)
            )),
            adapter("WildStacker", new StackAmountService.SnapshotData(
                Map.of(position, 5L),
                Map.of(position, "minecraft:diamond_block"),
                Map.of(ENTITY_ID, 7L)
            ))
        ));

        StackAmountService.StackSnapshot snapshot = first.snapshot(world(), 0, 10, 0, 10);

        assertEquals(8L, snapshot.blockAmount(block()));
        assertEquals(12L, snapshot.entityAmount(entity()));
        assertEquals("minecraft:diamond_block", snapshot.blockKeyOverride(block()));
    }

    @Test
    void directSpawnerLookupCanIncreaseSnapshotAmountAndFallbackIsPhysical() {
        StackAmountService service = new StackAmountService(List.of(
            new StackAmountService.Adapter("AdvancedSpawners", null, _block -> 32L, null, null, false, "test")
        ));
        StackAmountService.StackSnapshot stacked = service.snapshot(world(), 0, 10, 0, 10);
        StackAmountService.StackSnapshot physical = StackAmountService.physicalOnly().snapshot(world(), 0, 10, 0, 10);

        assertEquals(32L, stacked.blockAmount(block()));
        assertEquals(1L, physical.blockAmount(block()));
        assertEquals(1L, physical.entityAmount(entity()));
    }

    @Test
    void directEntityAndSpawnerSpawnResolversUseLargestLogicalAmount() {
        StackAmountService service = new StackAmountService(List.of(
            new StackAmountService.Adapter("RoseStacker", null, null, _entity -> 18L, null, true, "test"),
            new StackAmountService.Adapter("WildStacker", null, null, _entity -> 12L, _spawner -> 24L, true, "test")
        ));

        assertEquals(18L, service.entityAmount(livingEntity()));
        assertEquals(24L, service.spawnerSpawnAmount(spawner()));
        assertEquals(1L, StackAmountService.physicalOnly().entityAmount(livingEntity()));
        assertEquals(1L, StackAmountService.physicalOnly().spawnerSpawnAmount(spawner()));
    }

    private static StackAmountService.Adapter adapter(String pluginName, StackAmountService.SnapshotData data) {
        return new StackAmountService.Adapter(pluginName, _bounds -> data, null, null, null, true, "test");
    }

    private static World world() {
        return proxy(World.class, Map.of("getUID", WORLD_ID));
    }

    private static Block block() {
        return proxy(Block.class, Map.of(
            "getWorld", world(),
            "getX", 4,
            "getY", 70,
            "getZ", 6
        ));
    }

    private static Entity entity() {
        return proxy(Entity.class, Map.of("getUniqueId", ENTITY_ID));
    }

    private static LivingEntity livingEntity() {
        return proxy(LivingEntity.class, Map.of("getUniqueId", ENTITY_ID));
    }

    private static CreatureSpawner spawner() {
        return proxy(CreatureSpawner.class, Map.of());
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (_proxy, method, _args) -> values.getOrDefault(method.getName(), defaultValue(method.getReturnType()))
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0;
    }
}
