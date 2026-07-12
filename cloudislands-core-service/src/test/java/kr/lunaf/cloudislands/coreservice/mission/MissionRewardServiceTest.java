package kr.lunaf.cloudislands.coreservice.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.generator.InMemoryIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.InMemoryIslandWarehouseRepository;
import org.junit.jupiter.api.Test;

class MissionRewardServiceTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID ACTOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000702");

    @Test
    void appliesCoreBackedMissionRewards() {
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        InMemoryIslandGeneratorRepository generators = new InMemoryIslandGeneratorRepository();
        InMemoryIslandPermissionRuleRepository permissions = new InMemoryIslandPermissionRuleRepository();
        MissionRewardService rewards = new MissionRewardService(bank, limits, generators, permissions);

        assertEquals("BANK_DEPOSITED", rewards.apply(mission("BANK_DEPOSIT", "250 coins"), ACTOR_UUID).code());
        assertEquals("250.00", bank.balance(ISLAND_ID).balance());

        MissionRewardService.MissionRewardResult limit = rewards.apply(mission("LIMIT_INCREASE", "HOPPER 25"), ACTOR_UUID);
        assertEquals("LIMIT_INCREASED", limit.code());
        assertEquals("75", limit.details().get("value"));

        MissionRewardService.MissionRewardResult generator = rewards.apply(mission("GENERATOR_TIER", "default 3"), ACTOR_UUID);
        assertEquals("GENERATOR_TIER_SET", generator.code());
        assertEquals("3", generator.details().get("level"));

        MissionRewardService.MissionRewardResult permission = rewards.apply(mission("PERMISSION_TEMPORARY", "WITHDRAW_BANK 3600"), ACTOR_UUID);
        assertEquals("PERMISSION_TEMPORARY_GRANTED", permission.code());
        assertTrue(permissions.playerOverride(ISLAND_ID, ACTOR_UUID, IslandPermission.WITHDRAW_BANK).orElseThrow());
    }

    @Test
    void queuesCommandRewardsButRequiresDurableStorageForItems() {
        MissionRewardService rewards = new MissionRewardService(null, null, null, null);

        MissionRewardService.MissionRewardResult command = rewards.apply(mission("COMMAND", "give %player% diamond 1"), ACTOR_UUID);
        MissionRewardService.MissionRewardResult item = rewards.apply(mission("ITEM", "minecraft:diamond 1"), ACTOR_UUID);

        assertEquals("COMMAND_REWARD_QUEUED", command.code());
        assertEquals("give %player% diamond 1", command.details().get("command"));
        assertEquals("WAREHOUSE_REPOSITORY_UNAVAILABLE", item.code());
        assertEquals("UPGRADE_DISCOUNT_RECORDED", rewards.apply(mission("UPGRADE_DISCOUNT", "generator 10% 1h"), ACTOR_UUID).code());
    }

    @Test
    void depositsItemRewardsIntoDurableIslandWarehouse() {
        InMemoryIslandWarehouseRepository warehouse = new InMemoryIslandWarehouseRepository();
        MissionRewardService rewards = new MissionRewardService(null, null, null, null, warehouse);

        MissionRewardService.MissionRewardResult item = rewards.apply(mission("ITEM", "minecraft:diamond 12"), ACTOR_UUID);

        assertTrue(item.applied());
        assertEquals("ITEM_DEPOSITED_TO_WAREHOUSE", item.code());
        assertEquals("minecraft:diamond", item.details().get("materialKey"));
        assertEquals("12", item.details().get("amount"));
        assertEquals(12L, warehouse.list(ISLAND_ID, 10).getFirst().amount());
    }

    @Test
    void bankRewardAtStorageCapacityFailsWithoutChangingBalance() {
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();
        bank.deposit(ISLAND_ID, kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository.MAX_STORABLE_BALANCE);
        MissionRewardService rewards = new MissionRewardService(bank, null, null, null);

        MissionRewardService.MissionRewardResult result = rewards.apply(mission("BANK_DEPOSIT", "1 coin"), ACTOR_UUID);

        assertFalse(result.applied());
        assertEquals("BANK_LIMIT", result.code());
        assertEquals("999999999999999999.99", bank.balance(ISLAND_ID).balance());
    }

    @Test
    void limitRewardAtBigintCapacityFailsWithoutWrappingOrChangingLimit() {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        limits.set(ISLAND_ID, "HOPPER", Long.MAX_VALUE, ACTOR_UUID);
        MissionRewardService rewards = new MissionRewardService(null, limits, null, null);

        MissionRewardService.MissionRewardResult result = rewards.apply(mission("LIMIT_INCREASE", "HOPPER 1"), ACTOR_UUID);

        assertFalse(result.applied());
        assertEquals("LIMIT_REWARD_CAPACITY", result.code());
        assertEquals(Long.toString(Long.MAX_VALUE), result.details().get("value"));
        assertEquals(Long.MAX_VALUE, limits.list(ISLAND_ID).stream()
            .filter(limit -> limit.limitKey().equals("HOPPER"))
            .findFirst()
            .orElseThrow()
            .value());
    }

    private static IslandMissionSnapshot mission(String rewardType, String reward) {
        return new IslandMissionSnapshot(
            ISLAND_ID,
            "reward_contract",
            "MISSION",
            "reward",
            "Reward contract",
            "Reward contract",
            "BLOCK_BREAK",
            "*",
            1L,
            1L,
            true,
            rewardType,
            reward,
            false,
            false,
            Instant.parse("2026-01-02T03:04:05Z")
        );
    }
}
