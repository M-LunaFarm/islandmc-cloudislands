package kr.lunaf.cloudislands.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameplayParityPolicyTest {
    @Test
    void exposesStackedBlockLimitKeysAsStableGameplayContract() {
        assertEquals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK", GameplayParityPolicy.blockAmountLimitKey("minecraft:diamond block"));
        assertEquals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK", GameplayParityPolicy.blockAmountLimitKey("DIAMOND_BLOCK"));
        assertEquals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK", GameplayParityPolicy.blockAmountLimitKey("diamond-block"));
        assertTrue(GameplayParityPolicy.blockAmountLimit("block_amount:minecraft:diamond_block"));
        assertEquals("MINECRAFT:DIAMOND_BLOCK", GameplayParityPolicy.blockAmountMaterialKey("block_amount:minecraft:diamond_block"));
        assertEquals("", GameplayParityPolicy.blockAmountMaterialKey("HOPPER"));
        assertEquals("STACKED_BLOCKS_VISIBLE", GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY);
        assertEquals("WAREHOUSE_ROWS", GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY);
        assertEquals("HOPPER", GameplayParityPolicy.normalizeIslandLimitKey(" "));
        assertFalse(GameplayParityPolicy.blockAmountLimit(null));
    }

    @Test
    void exposesRoleLimitKeysAsStableGameplayContract() {
        assertEquals("ROLE_LIMIT:MODERATOR", GameplayParityPolicy.roleLimitKey("moderator"));
        assertTrue(GameplayParityPolicy.roleLimit("role_limit:trusted"));
        assertEquals("TRUSTED", GameplayParityPolicy.roleLimitRoleKey("role_limit:trusted"));
        assertEquals("", GameplayParityPolicy.roleLimitRoleKey("MEMBERS"));
        assertFalse(GameplayParityPolicy.roleLimit(null));
    }

    @Test
    void exposesEntityTypeLimitKeysAsStableGameplayContract() {
        assertEquals("ENTITY_TYPE:MINECRAFT:ZOMBIE", GameplayParityPolicy.entityTypeLimitKey("zombie"));
        assertEquals("ENTITY_TYPE:MINECRAFT:ZOMBIE_VILLAGER", GameplayParityPolicy.entityTypeLimitKey("zombie-villager"));
        assertTrue(GameplayParityPolicy.entityTypeLimit("entity_type:minecraft:zombie"));
        assertEquals("MINECRAFT:ZOMBIE", GameplayParityPolicy.entityTypeLimitEntityKey("entity_type:minecraft:zombie"));
        assertEquals("", GameplayParityPolicy.entityTypeLimitEntityKey("ENTITY"));
        assertFalse(GameplayParityPolicy.entityTypeLimit(null));
    }
}
