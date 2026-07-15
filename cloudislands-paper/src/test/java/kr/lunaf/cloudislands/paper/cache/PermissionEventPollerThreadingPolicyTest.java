package kr.lunaf.cloudislands.paper.cache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PermissionEventPollerThreadingPolicyTest {
    @Test
    void replicatedSynchronousEventsResolvePlayersAndFireOnTheGlobalThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));

        assertAwaitedGlobalCall(source, "new IslandPermissionCheckEvent");
        assertAwaitedGlobalCall(source, "new IslandPreVisitEvent");
        assertAwaitedGlobalCall(source, "new IslandVisitEvent");
        assertTrue(source.contains("PaperSchedulers.supply(plugin"));
        assertTrue(source.contains(".orTimeout(5L, TimeUnit.SECONDS).join()"),
            "the replay cursor must wait for the bounded global-thread event call to finish");
    }

    @Test
    void failedEventHandlingDoesNotAdvanceTheReplayCursorOrDeduplicationSet() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));
        int handler = source.indexOf("private void handleEvent(ParsedEvent event)");
        int publish = source.indexOf("publishLocalEvents(", handler);
        int markSeen = source.indexOf("markSeen(key);", handler);
        int cursor = source.indexOf("lastEventSequence = Math.max(lastEventSequence, event.sequence());", markSeen);

        assertTrue(handler >= 0 && publish > handler && markSeen > publish && cursor > markSeen,
            "an event must remain replayable until all local handling has been accepted");
    }

    @Test
    void migrationTicketCallbacksFenceTheInitiatingPlayerSessionOnGlobalThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));
        int create = source.indexOf("private void createMigrationReturnTicket");
        int wait = source.indexOf("private void waitMigrationReturnTicket", create);
        String method = source.substring(create, wait);

        assertTrue(method.contains("MigrationPlayerSession playerSession"));
        assertTrue(method.contains("waitMigrationReturnTicket(playerSession, ticket, 0)"));
        assertTrue(method.contains("PaperSchedulers.run(plugin, () -> migrationReturnRegistrationFailed(playerSession))"));
        assertTrue(source.contains("connectMigratingPlayer(playerSession, ticket)"));
        assertTrue(source.contains("if (currentMigrationPlayer(playerSession) == null)"),
            "a route session must not be published after the initiating player disconnects or reconnects");
        assertTrue(source.contains("return playerSession.isCurrent(player) ? player : null;"));
        assertTrue(source.contains("hideMigrationBossBar(playerSession, bossBar)"));
    }

    private void assertAwaitedGlobalCall(String source, String constructor) {
        int event = source.indexOf(constructor);
        int scheduler = source.lastIndexOf("callSynchronousEvent(() ->", event);
        assertTrue(event >= 0 && scheduler >= 0 && event - scheduler < 500,
            constructor + " must be constructed and fired through the awaited global scheduler callback");
    }
}
