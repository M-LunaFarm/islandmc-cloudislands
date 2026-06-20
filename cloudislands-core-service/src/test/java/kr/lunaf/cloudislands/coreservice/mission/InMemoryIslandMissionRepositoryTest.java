package kr.lunaf.cloudislands.coreservice.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import org.junit.jupiter.api.Test;

class InMemoryIslandMissionRepositoryTest {
    @Test
    void registeredProviderDefinitionsSeedIslandMissions() {
        InMemoryIslandMissionRepository repository = new InMemoryIslandMissionRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        repository.registerProviderDefinitions("addon.test", List.of(
            new MissionProviderDefinitionSnapshot("addon.test", "catch_fish", "CHALLENGE", "Catch Fish", 5L, "bait")
        ));

        var missions = repository.list(islandId, "CHALLENGE");
        assertTrue(missions.stream().anyMatch(mission -> mission.missionKey().equals("catch_fish") && mission.goal() == 5L));

        var progressed = repository.progress(islandId, actorUuid, "catch_fish", "CHALLENGE", 3L);
        assertTrue(progressed.isPresent());
        assertEquals(3L, progressed.get().progress());
        assertEquals("bait", progressed.get().reward());
    }
}
