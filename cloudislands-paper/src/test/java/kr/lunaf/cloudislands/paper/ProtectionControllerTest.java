package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.common.protection.ProtectionDecisionPolicy;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionControllerTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID VISITOR = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID BANNED = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000505");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000506");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000507");

    @Test
    void visitorFlagsDoNotBypassBannedOrMigrationState() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putFlag(ISLAND, IslandFlag.VISITOR_INTERACT, "true");
        cache.putRoleKey(ISLAND, BANNED, "BANNED");

        assertTrue(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT).allowed());
        assertFalse(protection.checkBlock(BANNED, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT).allowed());

        protection.markMigrating(ISLAND);

        var result = protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.INTERACT);
        assertFalse(result.allowed());
        assertEquals("ISLAND_MIGRATING", result.reason());
    }

    @Test
    void exposesSynchronousHotPathPolicy() {
        ProtectionController protection = new ProtectionController(new RegionIndex(), new LocalIslandPermissionCache());

        assertEquals(ProtectionDecisionPolicy.HOT_PATH_POLICY, protection.synchronousDecisionPolicy());
        assertEquals("no-core-api-http-database-or-redis-call-on-bukkit-event-thread", ProtectionDecisionPolicy.NO_SYNC_IO_POLICY);
    }

    @Test
    void dynamicRoleKeysCanGrantProtectionPermissions() {
        UUID builder = UUID.fromString("00000000-0000-0000-0000-000000000504");
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putRoleKey(ISLAND, builder, "builder");
        cache.putRuleKey(ISLAND, "builder", IslandPermission.BUILD, true);
        cache.putRuleKey(ISLAND, "builder", IslandPermission.BREAK, false);

        assertTrue(protection.memberOrTrusted(ISLAND, builder));
        assertEquals(RoleId.of("BUILDER"), protection.roleId(ISLAND, builder));
        assertTrue(protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BUILD).allowed());
        var denied = protection.checkBlock(builder, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK);
        assertFalse(denied.allowed());
        assertEquals(RoleId.of("BUILDER"), denied.effectiveRoleId());
        assertNull(denied.effectiveRole());
    }

    @Test
    void blankRoleKeysNormalizeToVisitorAtTheCacheBoundary() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        cache.putRoleKey(ISLAND, VISITOR, "  ");

        assertEquals(RoleId.of("VISITOR"), protection.roleId(ISLAND, VISITOR));
        assertFalse(protection.memberOrTrusted(ISLAND, VISITOR));
    }

    @Test
    void protectionSmokeMatrixCoversOwnerMemberTrustedVisitorBannedAndAdminBypass() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putRoleKey(ISLAND, OWNER, "OWNER");
        cache.putRoleKey(ISLAND, MEMBER, "MEMBER");
        cache.putRoleKey(ISLAND, TRUSTED, "TRUSTED");
        cache.putRoleKey(ISLAND, BANNED, "BANNED");
        cache.putRuleKey(ISLAND, "MEMBER", IslandPermission.BUILD, true);
        cache.putRuleKey(ISLAND, "TRUSTED", IslandPermission.OPEN_CONTAINER, true);

        assertTrue(protection.checkBlock(OWNER, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK).allowed(), "owner block break must be allowed");
        assertTrue(protection.checkBlock(MEMBER, "ci_shard_001", 0, 100, 0, IslandPermission.BUILD).allowed(), "member block place must follow role allow");
        assertTrue(protection.checkBlock(TRUSTED, "ci_shard_001", 0, 100, 0, IslandPermission.OPEN_CONTAINER).allowed(), "trusted container access must follow role allow");
        assertFalse(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK).allowed(), "visitor block break must be denied");
        assertFalse(protection.checkBlock(BANNED, "ci_shard_001", 0, 100, 0, IslandPermission.OPEN_CONTAINER).allowed(), "banned player container access must be denied");
        assertTrue(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, IslandPermission.BREAK, true).allowed(), "admin bypass must allow protected actions");
    }

    @Test
    void cropTramplingUsesBuildPermissionNotVisitorInteractFlag() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("event.getAction() == Action.PHYSICAL"), "crop trampling must be classified from the physical interact event");
        assertTrue(listener.contains("event.getClickedBlock().getType() == Material.FARMLAND"), "farmland trampling must be explicitly protected");
        assertTrue(listener.contains("return IslandPermission.BUILD;"), "farmland trampling must require build permission, not visitor interact");
    }

    @Test
    void fertilizationRequiresItsOwnPermissionAndCannotGrowAcrossIslandBoundaries() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onFertilize(BlockFertilizeEvent event)"));
        assertTrue(listener.contains("IslandPermission.FERTILIZE"));
        assertTrue(listener.contains("anyMatch(state -> !sameIsland(event.getBlock(), state.getBlock()))"));
        assertTrue(listener.contains("onStructureGrow(StructureGrowEvent event)"));
        assertTrue(listener.contains("if (event.isFromBonemeal())"));
        assertTrue(listener.contains("reportBlockReplacement(state.getBlock(), state.getType())"));
    }

    @Test
    void animalFishingRideAndTradingActionsUseGranularPermissions() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onBreed(EntityBreedEvent event)"));
        assertTrue(listener.contains("IslandPermission.ANIMAL_BREED"));
        assertTrue(listener.contains("IslandPermission.ANIMAL_SHEAR"));
        assertTrue(listener.contains("onFish(PlayerFishEvent event)"));
        assertTrue(listener.contains("IslandPermission.FISH"));
        assertTrue(listener.contains("onVehicleEnter(VehicleEnterEvent event)"));
        assertTrue(listener.contains("IslandPermission.ENTITY_RIDE"));
        assertTrue(listener.contains("entity instanceof AbstractVillager"));
        assertTrue(listener.contains("IslandPermission.VILLAGER_TRADE"));
        assertTrue(listener.contains("animals.isBreedItem"));
    }

    @Test
    void visitorInteractFlagPreservesGranularInteractionCompatibility() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        ProtectionController protection = new ProtectionController(new RegionIndex(), cache);
        protection.registerIsland(ISLAND, "ci_shard_001", 0, 0, 300, 2, 3);
        cache.putRoleKey(ISLAND, VISITOR, "VISITOR");
        cache.putFlag(ISLAND, IslandFlag.VISITOR_INTERACT, "true");

        for (IslandPermission permission : java.util.List.of(
            IslandPermission.ANIMAL_BREED,
            IslandPermission.ANIMAL_SHEAR,
            IslandPermission.FISH,
            IslandPermission.ENTITY_RIDE,
            IslandPermission.VILLAGER_TRADE,
            IslandPermission.PICKUP_ENTITY_BUCKET,
            IslandPermission.TAKE_LECTERN_BOOK,
            IslandPermission.DYE_SHEEP,
            IslandPermission.SADDLE_ENTITY,
            IslandPermission.BRUSH,
            IslandPermission.IGNITE_CREEPER,
            IslandPermission.NAME_ENTITY,
            IslandPermission.SCULK_SENSOR,
            IslandPermission.TURTLE_EGG_TRAMPLE
        )) {
            assertTrue(protection.checkBlock(VISITOR, "ci_shard_001", 0, 100, 0, permission).allowed(), permission.name());
        }
    }

    @Test
    void itemEntityAndSpecialTeleportActionsUseGranularPermissions() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onBucketEntity(PlayerBucketEntityEvent event)"));
        assertTrue(listener.contains("IslandPermission.PICKUP_ENTITY_BUCKET"));
        assertTrue(listener.contains("onTakeLecternBook(PlayerTakeLecternBookEvent event)"));
        assertTrue(listener.contains("IslandPermission.TAKE_LECTERN_BOOK"));
        assertTrue(listener.contains("entity instanceof Sheep"));
        assertTrue(listener.contains("IslandPermission.DYE_SHEEP"));
        assertTrue(listener.contains("held == Material.SADDLE"));
        assertTrue(listener.contains("IslandPermission.SADDLE_ENTITY"));
        assertTrue(listener.contains("event.getItem().getType() == Material.BRUSH"));
        assertTrue(listener.contains("IslandPermission.BRUSH"));
        assertTrue(listener.contains("onSpecialTeleport(PlayerTeleportEvent event)"));
        assertTrue(listener.contains("cause.equals(\"ENDER_PEARL\")"));
        assertTrue(listener.contains("cause.equals(\"CHORUS_FRUIT\")"));
    }

    @Test
    void spawnerPaintingTurtleEggAndWindChargeUseGranularPermissions() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("Material.SPAWNER ? IslandPermission.BREAK_SPAWNER"));
        assertTrue(listener.contains("EntityType.PAINTING ? IslandPermission.PAINTING"));
        assertTrue(listener.contains("Material.TURTLE_EGG"));
        assertTrue(listener.contains("IslandPermission.TURTLE_EGG_TRAMPLE"));
        assertTrue(listener.contains("EntityType.WIND_CHARGE"));
        assertTrue(listener.contains("EntityType.BREEZE_WIND_CHARGE"));
        assertTrue(listener.contains("IslandPermission.WIND_CHARGE"));
    }

    @Test
    void projectilesFrostWalkerAndRemainingPhysicalInteractionsAreProtected() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onPickupArrow(PlayerPickupArrowEvent event)"));
        assertTrue(listener.contains("onProjectileLaunch(ProjectileLaunchEvent event)"));
        assertTrue(listener.contains("case TRIDENT -> IslandPermission.PICKUP_ITEM"));
        assertTrue(listener.contains("case ENDER_PEARL -> IslandPermission.ENDER_PEARL"));
        assertTrue(listener.contains("case WIND_CHARGE -> IslandPermission.WIND_CHARGE"));
        assertTrue(listener.contains("onFrostWalker(EntityBlockFormEvent event)"));
        assertTrue(listener.contains("held == Material.NAME_TAG"));
        assertTrue(listener.contains("IslandPermission.NAME_ENTITY"));
        assertTrue(listener.contains("entity instanceof Creeper"));
        assertTrue(listener.contains("IslandPermission.IGNITE_CREEPER"));
        assertTrue(listener.contains("calibrated_sculk_sensor"));
        assertTrue(listener.contains("sculk_shrieker"));
        assertTrue(listener.contains("IslandPermission.SCULK_SENSOR"));
    }

    @Test
    void automationBoundariesMobTargetingAndRaidsAreProtected() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("onBucketDispense(BlockDispenseEvent event)"));
        assertTrue(listener.contains("onBucketDispenseCount(BlockDispenseEvent event)"));
        assertTrue(listener.contains("priority = EventPriority.MONITOR"));
        assertTrue(listener.contains("!sameIsland(event.getBlock(), target)"));
        assertTrue(listener.contains("onEntityTarget(EntityTargetLivingEntityEvent event)"));
        assertTrue(listener.contains("IslandPermission.ATTACK_MOB"));
        assertTrue(listener.contains("onRaidTrigger(RaidTriggerEvent event)"));
        assertTrue(listener.contains("IslandPermission.TRIGGER_RAID"));
    }

    @Test
    void naturalSpreadGrowthAndDependentBreaksKeepAccurateBlockDeltas() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("BlockSpreadPolicy.fireSpread"));
        assertTrue(listener.contains("onSpreadCount(BlockSpreadEvent event)"));
        assertTrue(listener.contains("onBlockGrowCount(BlockGrowEvent event)"));
        assertTrue(listener.contains("event.getBlock().getType() != event.getNewState().getType()"));
        assertTrue(listener.contains("onDependentBlockBreak(BlockBreakBlockEvent event)"));
        assertTrue(listener.contains("blockDeltas.broken(islandId, event.getBlock())"));
    }

    @Test
    void ss2NaturalGameplayFlagsDefaultAllowUntilExplicitlyDisabled() throws Exception {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        for (IslandFlag flag : java.util.List.of(
            IslandFlag.CROPS_GROWTH,
            IslandFlag.TREE_GROWTH,
            IslandFlag.EGG_LAY,
            IslandFlag.GHAST_FIREBALL
        )) {
            assertTrue(cache.flagAllowedOrDefault(ISLAND, flag, true), flag.name());
            cache.putFlag(ISLAND, flag, "false");
            assertFalse(cache.flagAllowedOrDefault(ISLAND, flag, true), flag.name());
            cache.putFlag(ISLAND, flag, "enabled");
            assertTrue(cache.flagAllowedOrDefault(ISLAND, flag, true), flag.name());
        }

        String protectionListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));
        String cropListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/generator/IslandCropGrowthListener.java"));
        assertTrue(cropListener.contains("IslandFlag.CROPS_GROWTH, true"));
        assertTrue(protectionListener.contains("IslandFlag.TREE_GROWTH, true"));
        assertTrue(protectionListener.contains("IslandFlag.EGG_LAY, true"));
        assertTrue(protectionListener.contains("return IslandFlag.GHAST_FIREBALL"));
    }

    @Test
    void perPlayerTimeAndWeatherOverridesDoNotMutateSharedShardWorlds() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandGameplayFlagListener.java"));

        assertTrue(listener.contains("player.setPlayerTime(1000L, false)"));
        assertTrue(listener.contains("player.setPlayerTime(6000L, false)"));
        assertTrue(listener.contains("player.setPlayerTime(13000L, false)"));
        assertTrue(listener.contains("player.setPlayerTime(18000L, false)"));
        assertTrue(listener.contains("player.setPlayerWeather(WeatherType.DOWNFALL)"));
        assertTrue(listener.contains("player.setPlayerWeather(WeatherType.CLEAR)"));
        assertTrue(listener.contains("player.resetPlayerTime()"));
        assertTrue(listener.contains("player.resetPlayerWeather()"));
        assertTrue(listener.contains("environmentOverrides.put(player.getUniqueId(), desired)"));
        assertFalse(listener.contains("block.getWorld().setTime"));
        assertFalse(listener.contains("block.getWorld().setStorm"));
    }

    @Test
    void roleCatalogUsesRoleKeysForDefaultSuggestions() {
        LocalIslandPermissionCache cache = new LocalIslandPermissionCache();
        cache.putRoleDefinition(ISLAND, "builder");

        assertEquals(java.util.List.of("BUILDER", "CO_OWNER", "MEMBER", "MODERATOR", "TRUSTED"), cache.roleCatalog(ISLAND, false));
        assertTrue(cache.roleCatalog(ISLAND, true).contains("VISITOR"));
        assertFalse(cache.roleCatalog(ISLAND, false).contains("OWNER"));
        assertFalse(cache.roleCatalog(ISLAND, false).contains("BANNED"));
    }
}
