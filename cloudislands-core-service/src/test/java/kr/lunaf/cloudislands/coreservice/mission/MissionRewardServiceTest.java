package kr.lunaf.cloudislands.coreservice.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.generator.InMemoryIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
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
        assertEquals("250", bank.balance(ISLAND_ID).balance());

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
    void queuesPaperDeliveredMissionRewards() {
        MissionRewardService rewards = new MissionRewardService(null, null, null, null);

        MissionRewardService.MissionRewardResult command = rewards.apply(mission("COMMAND", "give %player% diamond 1"), ACTOR_UUID);
        MissionRewardService.MissionRewardResult item = rewards.apply(mission("ITEM", "minecraft:diamond 1"), ACTOR_UUID);

        assertEquals("COMMAND_REWARD_QUEUED", command.code());
        assertEquals("give %player% diamond 1", command.details().get("command"));
        assertEquals("ITEM_REWARD_QUEUED", item.code());
        assertEquals("minecraft:diamond 1", item.details().get("item"));
        assertEquals("UPGRADE_DISCOUNT_RECORDED", rewards.apply(mission("UPGRADE_DISCOUNT", "generator 10% 1h"), ACTOR_UUID).code());
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
