package kr.lunaf.cloudislands.paper.cache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PermissionEventPollerThreadingPolicyTest {
    @Test
    void replicatedSynchronousEventsResolvePlayersAndFireOnTheGlobalThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));

        assertScheduled(source, "new IslandPermissionCheckEvent");
        assertScheduled(source, "new IslandPreVisitEvent");
        assertScheduled(source, "new IslandVisitEvent");
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

    private void assertScheduled(String source, String constructor) {
        int event = source.indexOf(constructor);
        int scheduler = source.lastIndexOf("PaperSchedulers.run(plugin", event);
        assertTrue(event >= 0 && scheduler >= 0 && event - scheduler < 500,
            constructor + " must be constructed and fired inside the global scheduler callback");
    }
}
