package kr.lunaf.cloudislands.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EventApiSurfacePolicyTest {
    @Test
    void pinsLocalPaperEventSurfaceFromGoal() {
        assertEquals(
            List.of(
                "IslandPreCreateEvent",
                "IslandCreateEvent",
                "IslandDeleteEvent",
                "IslandPreActivateEvent",
                "IslandActivateEvent",
                "IslandDeactivateEvent",
                "IslandPreVisitEvent",
                "IslandVisitEvent",
                "IslandMemberJoinEvent",
                "IslandMemberLeaveEvent",
                "IslandRoleChangeEvent",
                "IslandFlagChangeEvent",
                "IslandPermissionCheckEvent",
                "IslandLevelRecalculateEvent",
                "IslandWorthChangeEvent",
                "IslandWarpCreateEvent",
                "IslandWarpDeleteEvent",
                "IslandBiomeChangeEvent",
                "IslandUpgradeEvent"
            ),
            EventApiSurfacePolicy.localPaperEvents()
        );
        assertTrue(EventApiSurfacePolicy.localPaperEvent("IslandVisitEvent"));
        assertFalse(EventApiSurfacePolicy.localPaperEvent("RawDatabaseEvent"));
        assertEquals("paper-local-events-are-plugin-api-surface-not-cross-node-transport", EventApiSurfacePolicy.LOCAL_EVENT_POLICY);
    }

    @Test
    void mapsLocalPaperEventsToStableGlobalTypes() {
        assertEquals(CloudIslandEventType.ISLAND_PRE_CREATE, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandPreCreateEvent"));
        assertEquals(CloudIslandEventType.ISLAND_CREATED, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandCreateEvent"));
        assertEquals(CloudIslandEventType.ISLAND_DELETED, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandDeleteEvent"));
        assertEquals(CloudIslandEventType.ISLAND_ACTIVATED, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandActivateEvent"));
        assertEquals(CloudIslandEventType.ISLAND_VISITED, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandVisitEvent"));
        assertEquals(CloudIslandEventType.ISLAND_LEVEL_UPDATED, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandLevelRecalculateEvent"));
        assertEquals(CloudIslandEventType.ISLAND_UPGRADE, EventApiSurfacePolicy.globalTypeForLocalEvent("IslandUpgradeEvent"));
        assertNull(EventApiSurfacePolicy.globalTypeForLocalEvent("UnknownEvent"));
    }

    @Test
    void pinsRequiredGlobalEventsForCrossNodeDelivery() {
        assertEquals(
            List.of(
                CloudIslandEventType.ISLAND_CREATED,
                CloudIslandEventType.ISLAND_DELETED,
                CloudIslandEventType.ISLAND_ACTIVATED,
                CloudIslandEventType.ISLAND_DEACTIVATED,
                CloudIslandEventType.ISLAND_MIGRATED,
                CloudIslandEventType.ISLAND_MEMBER_CHANGED,
                CloudIslandEventType.ISLAND_FLAG_CHANGED,
                CloudIslandEventType.ISLAND_LEVEL_UPDATED,
                CloudIslandEventType.ISLAND_SNAPSHOT_CREATED,
                CloudIslandEventType.NODE_STATE_CHANGED,
                CloudIslandEventType.ROUTE_TICKET_CREATED,
                CloudIslandEventType.ROUTE_TICKET_CONSUMED
            ),
            EventApiSurfacePolicy.requiredGlobalEvents()
        );
        assertTrue(EventApiSurfacePolicy.requiredGlobalEvent(CloudIslandEventType.NODE_STATE_CHANGED));
        assertTrue(EventApiSurfacePolicy.requiredGlobalEvent(CloudIslandEventType.ROUTE_TICKET_CONSUMED));
        assertFalse(EventApiSurfacePolicy.requiredGlobalEvent(CloudIslandEventType.CORE_RELOADED));
        assertEquals(
            "global-events-are-redis-stream-append-only-ordered-cache-invalidation-and-addon-lifecycle-contract",
            EventApiSurfacePolicy.GLOBAL_EVENT_POLICY
        );
    }

    @Test
    void recordsRedisStreamAndAddonDeliveryPolicy() {
        assertEquals("redis-streams-are-append-only-event-log-for-cross-node-ordering-and-replay", EventApiSurfacePolicy.REDIS_STREAM_POLICY);
        assertEquals("core-global-events-to-paper-poller-to-cloudislands-addon-and-bukkit-events", EventApiSurfacePolicy.ADDON_DELIVERY_POLICY);
        assertTrue(EventApiSurfacePolicy.globalEventSummary().contains("ISLAND_SNAPSHOT_CREATED"));
        assertTrue(EventApiSurfacePolicy.localEventSummary().contains("IslandPermissionCheckEvent"));
    }

    @Test
    void requiredGlobalEventsHavePublisherAndConsumerCoverage() throws Exception {
        Path root = workspaceRoot();
        String corePublishers = sourceTree(root.resolve("cloudislands-core-service/src/main/java"), path -> !path.toString().contains("/metrics/"));
        String paperPoller = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));

        assertTrue(paperPoller.contains("new CloudIslandsGlobalEvent"), "Paper poller must publish the generic addon-visible event");
        for (CloudIslandEventType eventType : EventApiSurfacePolicy.requiredGlobalEvents()) {
            assertTrue(
                corePublishers.contains("CloudIslandEventType." + eventType.name()),
                eventType + " must be referenced by a Core publisher source"
            );
            assertTrue(
                paperPoller.contains("CloudIslandEventType." + eventType.name() + ".name()"),
                eventType + " must be consumed by the Paper event poller"
            );
        }
    }

    private static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate : List.of(current, current.getParent())) {
            if (candidate != null && Files.isDirectory(candidate.resolve("cloudislands-core-service/src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate CloudIslands workspace root from " + current);
    }

    private static String sourceTree(Path root, java.util.function.Predicate<Path> include) throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".java")).filter(include).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
    }
}
