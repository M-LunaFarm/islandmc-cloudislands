package kr.lunaf.cloudislands.paper.integration.customitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class CustomBlockKeyServiceTest {
    @Test
    void customBlockAndFurnitureIdsOverrideVanillaCarrierTypes() {
        Block block = block(Material.NOTE_BLOCK);
        Entity furniture = entity(EntityType.ITEM_DISPLAY);
        CustomBlockKeyService service = new CustomBlockKeyService(List.of(
            new CustomBlockKeyService.Adapter("Oraxen", ignored -> "ruby_block", ignored -> "ruby_chair", "test")
        ));

        assertEquals("oraxen:ruby_block", service.blockKey(block));
        assertEquals("oraxen:ruby_chair", service.entityKey(furniture));
        assertTrue(service.supports("Oraxen"));
        assertTrue(service.runtimeDetails("Oraxen").get("consumers").contains("ranking"));
    }

    @Test
    void unresolvedCustomApisRetainStableVanillaKeys() {
        CustomBlockKeyService service = CustomBlockKeyService.vanillaOnly();

        assertEquals("minecraft:stone", service.blockKey(block(Material.STONE)));
        assertEquals("entity:minecraft:cow", service.entityKey(entity(EntityType.COW)));
        assertFalse(service.supports("ItemsAdder"));
        assertEquals("itemsadder:namespace:machine", CustomBlockKeyService.customKey("ItemsAdder", "namespace:machine"));
    }

    private static Block block(Material material) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> material;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Entity entity(EntityType entityType) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> entityType;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
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
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
