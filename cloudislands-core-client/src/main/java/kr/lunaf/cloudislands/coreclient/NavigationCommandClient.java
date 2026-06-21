package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface NavigationCommandClient {
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName);

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID islandId);

    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName);

    CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid);

    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);

    CompletableFuture<ReviewActionView> setReview(UUID islandId, UUID reviewerUuid, int rating, String comment);
}
