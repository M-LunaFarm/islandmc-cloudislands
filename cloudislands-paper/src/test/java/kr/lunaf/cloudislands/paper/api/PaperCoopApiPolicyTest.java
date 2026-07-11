package kr.lunaf.cloudislands.paper.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperCoopApiPolicyTest {
    @Test
    void publicCoopRemovalCannotPromotePlayersIntoPermanentMembership() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"));

        assertTrue(source.contains("mutate(\"island.coop.add\", () -> client.memberCommands().setRole(islandId, actorUuid, targetUuid, \"TRUSTED\")"));
        assertTrue(source.contains("mutateIdempotent(\"island.coop.remove\", () -> client.memberCommands().removeMember(islandId, actorUuid, targetUuid)"));
        assertTrue(source.contains("action(view, \"COOP_ADDED\")"));
        assertTrue(source.contains("action(view, \"COOP_REMOVED\")"));
    }
}
