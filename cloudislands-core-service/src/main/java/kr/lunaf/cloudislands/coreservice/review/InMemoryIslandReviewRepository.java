package kr.lunaf.cloudislands.coreservice.review;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;

public final class InMemoryIslandReviewRepository implements IslandReviewRepository {
    private final Map<UUID, Map<UUID, IslandReviewSnapshot>> reviews = new ConcurrentHashMap<>();

    @Override
    public synchronized IslandReviewSnapshot upsert(UUID islandId, UUID reviewerUuid, int rating, String comment) {
        Instant now = Instant.now();
        IslandReviewSnapshot previous = reviews.getOrDefault(islandId, Map.of()).get(reviewerUuid);
        IslandReviewSnapshot snapshot = new IslandReviewSnapshot(
            islandId,
            reviewerUuid,
            IslandReviewSnapshot.normalizeRating(rating),
            IslandReviewSnapshot.normalizeComment(comment),
            previous == null ? now : previous.createdAt(),
            now
        );
        reviews.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(reviewerUuid, snapshot);
        return snapshot;
    }

    @Override
    public Optional<IslandReviewSnapshot> find(UUID islandId, UUID reviewerUuid) {
        return Optional.ofNullable(reviews.getOrDefault(islandId, Map.of()).get(reviewerUuid));
    }

    @Override
    public List<IslandReviewSnapshot> list(UUID islandId, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return reviews.getOrDefault(islandId, Map.of()).values().stream()
            .sorted(Comparator.comparing(IslandReviewSnapshot::updatedAt).reversed())
            .limit(cappedLimit)
            .toList();
    }

    @Override
    public List<IslandReviewRankSnapshot> topByRating(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return reviews.entrySet().stream()
            .map(entry -> rank(entry.getKey(), entry.getValue()))
            .filter(rank -> rank.reviewCount() > 0)
            .sorted(Comparator.comparing(IslandReviewRankSnapshot::averageRating).reversed()
                .thenComparing(IslandReviewRankSnapshot::reviewCount, Comparator.reverseOrder())
                .thenComparing(IslandReviewRankSnapshot::updatedAt, Comparator.reverseOrder()))
            .limit(cappedLimit)
            .toList();
    }

    @Override
    public synchronized boolean delete(UUID islandId, UUID reviewerUuid) {
        Map<UUID, IslandReviewSnapshot> islandReviews = reviews.get(islandId);
        return islandReviews != null && islandReviews.remove(reviewerUuid) != null;
    }

    private IslandReviewRankSnapshot rank(UUID islandId, Map<UUID, IslandReviewSnapshot> islandReviews) {
        double average = islandReviews.values().stream().mapToInt(IslandReviewSnapshot::rating).average().orElse(0.0D);
        Instant updatedAt = islandReviews.values().stream()
            .map(IslandReviewSnapshot::updatedAt)
            .max(Comparator.naturalOrder())
            .orElse(Instant.EPOCH);
        return new IslandReviewRankSnapshot(islandId, average, islandReviews.size(), updatedAt);
    }
}
