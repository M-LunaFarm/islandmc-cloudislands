package kr.lunaf.cloudislands.coreservice.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;

public interface IslandReviewRepository {
    IslandReviewSnapshot upsert(UUID islandId, UUID reviewerUuid, int rating, String comment);
    Optional<IslandReviewSnapshot> find(UUID islandId, UUID reviewerUuid);
    List<IslandReviewSnapshot> list(UUID islandId, int limit);
    List<IslandReviewRankSnapshot> topByRating(int limit);
    boolean delete(UUID islandId, UUID reviewerUuid);
}
