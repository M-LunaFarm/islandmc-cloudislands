package kr.lunaf.cloudislands.coreservice.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import org.junit.jupiter.api.Test;

class InMemoryIslandMissionRepositoryTest {
    @Test
    void missionAndChallengeWithTheSameKeyKeepIndependentProgress() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        repository.registerProviderDefinitions("addon.dual-kind", List.of(
            definition("shared_key", "MISSION", 3L),
            definition("shared_key", "CHALLENGE", 7L)
        ));

        assertEquals(2L, repository.progress(islandId, actorUuid, "shared_key", "MISSION", 2L).orElseThrow().progress());
        assertEquals(5L, repository.progress(islandId, actorUuid, "shared_key", "CHALLENGE", 5L).orElseThrow().progress());
        assertEquals(2L, repository.list(islandId, "MISSION").stream()
            .filter(mission -> mission.missionKey().equals("shared_key"))
            .findFirst().orElseThrow().progress());
        assertEquals(5L, repository.list(islandId, "CHALLENGE").stream()
            .filter(mission -> mission.missionKey().equals("shared_key"))
            .findFirst().orElseThrow().progress());
    }

    @Test
    void registeredProviderDefinitionsSeedIslandMissions() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        repository.registerProviderDefinitions("addon.test", List.of(
            new MissionProviderDefinitionSnapshot(
                "addon.test",
                "catch_fish",
                "CHALLENGE",
                "fishing",
                "Catch Fish",
                "Catch fish around the island",
                "FISH_CAUGHT",
                "minecraft:cod",
                5L,
                "ITEM",
                "bait",
                true,
                true,
                true,
                null
            )
        ));

        var missions = repository.list(islandId, "CHALLENGE");
        assertTrue(missions.stream().anyMatch(mission -> mission.missionKey().equals("catch_fish") && mission.goal() == 5L));

        var progressed = repository.progress(islandId, actorUuid, "catch_fish", "CHALLENGE", 3L);
        assertTrue(progressed.isPresent());
        assertEquals(3L, progressed.get().progress());
        assertEquals("fishing", progressed.get().category());
        assertEquals("Catch fish around the island", progressed.get().description());
        assertEquals("FISH_CAUGHT", progressed.get().triggerType());
        assertEquals("minecraft:cod", progressed.get().targetKey());
        assertEquals("ITEM", progressed.get().rewardType());
        assertEquals("bait", progressed.get().reward());
        assertTrue(progressed.get().repeatable());
        assertTrue(progressed.get().dailyReset());
    }

    @Test
    void nonRepeatableMissionCannotBeCompletedOrProgressedTwice() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        var first = repository.complete(islandId, actorUuid, "first_blocks", "MISSION");
        var secondComplete = repository.complete(islandId, actorUuid, "first_blocks", "MISSION");
        var secondProgress = repository.progress(islandId, actorUuid, "first_blocks", "MISSION", 1L);

        assertTrue(first.isPresent());
        assertTrue(first.get().completed());
        assertTrue(secondComplete.isEmpty());
        assertTrue(secondProgress.isEmpty());
    }

    @Test
    void absoluteProgressTracksAuthoritativeValueWithoutDoubleCountingOrRegressing() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        assertEquals(600L, repository.progressTo(islandId, actorUuid, "bank_balance", "MISSION", 600L).orElseThrow().progress());
        assertEquals(600L, repository.progressTo(islandId, actorUuid, "bank_balance", "MISSION", 400L).orElseThrow().progress());
        var completed = repository.progressTo(islandId, actorUuid, "bank_balance", "MISSION", 1_000L).orElseThrow();

        assertEquals(1_000L, completed.progress());
        assertTrue(completed.completed());
        assertTrue(repository.progressTo(islandId, actorUuid, "bank_balance", "MISSION", 1_500L).isEmpty());
    }

    @Test
    void failedRewardCanReopenCompletionForRetry() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        assertTrue(repository.complete(islandId, actorUuid, "first_blocks", "MISSION").orElseThrow().completed());
        assertTrue(repository.reopenAfterRewardFailure(islandId, "first_blocks", "MISSION"));

        var retry = repository.complete(islandId, actorUuid, "first_blocks", "MISSION");
        assertTrue(retry.isPresent());
        assertTrue(retry.orElseThrow().completed());
    }

    @Test
    void repeatableMissionMustResetBeforeNextProgressCanReward() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        repository.registerProviderDefinitions("addon.test", List.of(
            new MissionProviderDefinitionSnapshot("addon.test", "repeat", "MISSION", "", "Repeat", "", "BLOCK_BREAK", "*", 2L, "ITEM", "STONE 1", true, false, true, null)
        ));

        assertTrue(repository.progress(islandId, actorUuid, "repeat", "MISSION", 2L).orElseThrow().completed());
        assertTrue(repository.progress(islandId, actorUuid, "repeat", "MISSION", 1L).isEmpty());
        assertTrue(repository.resetRepeatableAfterReward(islandId, "repeat", "MISSION"));

        var nextCycle = repository.progress(islandId, actorUuid, "repeat", "MISSION", 1L).orElseThrow();
        assertEquals(1L, nextCycle.progress());
        assertTrue(!nextCycle.completed());
    }

    private static MissionProviderDefinitionSnapshot definition(String key, String kind, long goal) {
        return new MissionProviderDefinitionSnapshot(
            "addon.dual-kind", key, kind, "identity", key, "", "BLOCK_BREAK", "*", goal,
            "", "", false, false, true, null
        );
    }
}
