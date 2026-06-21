package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NavigationCommandClient {
    CompletableFuture<ReviewActionView> setReview(UUID islandId, UUID reviewerUuid, int rating, String comment);
}
