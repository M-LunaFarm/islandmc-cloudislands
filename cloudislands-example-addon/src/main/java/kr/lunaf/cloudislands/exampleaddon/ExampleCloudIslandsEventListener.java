package kr.lunaf.cloudislands.exampleaddon;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.IslandMissionProgressEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketCreatedEvent;

public final class ExampleCloudIslandsEventListener {
    private final AtomicLong observedRouteTickets = new AtomicLong();
    private final AtomicLong completedMissionEvents = new AtomicLong();
    private volatile String latestRouteTarget = "";

    public void onCloudEvent(CloudEvent event) {
        if (event instanceof RouteTicketCreatedEvent routeTicket) {
            observedRouteTickets.incrementAndGet();
            latestRouteTarget = routeTicket.targetNode() + "/" + routeTicket.targetServerName();
        } else if (event instanceof IslandMissionProgressEvent missionProgress && missionProgress.completed()) {
            completedMissionEvents.incrementAndGet();
        }
    }

    public long observedRouteTickets() {
        return observedRouteTickets.get();
    }

    public long completedMissionEvents() {
        return completedMissionEvents.get();
    }

    public String latestRouteTarget() {
        return latestRouteTarget;
    }

    public String playerStatusLine(UUID playerUuid) {
        String player = playerUuid == null ? "unknown-player" : playerUuid.toString();
        return "player=" + player
            + " routeTickets=" + observedRouteTickets()
            + " completedMissions=" + completedMissionEvents()
            + " latestRoute=" + latestRouteTarget();
    }
}
