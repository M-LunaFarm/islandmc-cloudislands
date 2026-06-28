package kr.lunaf.cloudislands.coreservice.review;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandReviewModerationSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;

public final class InMemoryIslandReviewRepository implements IslandReviewRepository {
    private final Map<UUID, Map<UUID, IslandReviewSnapshot>> reviews = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, IslandReviewModerationSnapshot>> moderation = new ConcurrentHashMap<>();

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
        moderation.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .putIfAbsent(reviewerUuid, new IslandReviewModerationSnapshot(islandId, reviewerUuid, "VISIBLE", 0, "", null, Instant.EPOCH, "", now));
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
            .filter(review -> moderation(review.islandId(), review.reviewerUuid()).publiclyVisible())
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
        boolean removed = islandReviews != null && islandReviews.remove(reviewerUuid) != null;
        Map<UUID, IslandReviewModerationSnapshot> islandModeration = moderation.get(islandId);
        if (islandModeration != null) {
            islandModeration.remove(reviewerUuid);
        }
        return removed;
    }

    @Override
    public synchronized Optional<IslandReviewModerationSnapshot> report(UUID islandId, UUID reviewerUuid, UUID reporterUuid, String reason) {
        if (find(islandId, reviewerUuid).isEmpty()) {
            return Optional.empty();
        }
        IslandReviewModerationSnapshot current = moderation(islandId, reviewerUuid);
        String nextState = current.moderationState().equals("HIDDEN") ? "HIDDEN" : "REPORTED";
        IslandReviewModerationSnapshot next = new IslandReviewModerationSnapshot(
            islandId,
            reviewerUuid,
            nextState,
            current.reportCount() + 1,
            reason,
            current.moderatedBy(),
            current.moderatedAt(),
            current.moderationNote(),
            Instant.now()
        );
        moderation.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(reviewerUuid, next);
        return Optional.of(next);
    }

    @Override
    public synchronized Optional<IslandReviewModerationSnapshot> moderate(UUID islandId, UUID reviewerUuid, String moderationState, UUID moderatorUuid, String note) {
        if (find(islandId, reviewerUuid).isEmpty()) {
            return Optional.empty();
        }
        IslandReviewModerationSnapshot current = moderation(islandId, reviewerUuid);
        IslandReviewModerationSnapshot next = new IslandReviewModerationSnapshot(
            islandId,
            reviewerUuid,
            moderationState,
            current.reportCount(),
            current.reportReason(),
            moderatorUuid,
            Instant.now(),
            note,
            Instant.now()
        );
        moderation.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(reviewerUuid, next);
        return Optional.of(next);
    }

    @Override
    public List<IslandReviewModerationSnapshot> moderationQueue(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return moderation.values().stream()
            .flatMap(island -> island.values().stream())
            .filter(snapshot -> !snapshot.moderationState().equals("VISIBLE"))
            .sorted(Comparator.comparing(IslandReviewModerationSnapshot::reportCount).reversed()
                .thenComparing(IslandReviewModerationSnapshot::updatedAt, Comparator.reverseOrder()))
            .limit(cappedLimit)
            .toList();
    }

    private IslandReviewRankSnapshot rank(UUID islandId, Map<UUID, IslandReviewSnapshot> islandReviews) {
        List<IslandReviewSnapshot> visibleReviews = islandReviews.values().stream()
            .filter(review -> moderation(review.islandId(), review.reviewerUuid()).publiclyVisible())
            .toList();
        double average = visibleReviews.stream().mapToInt(IslandReviewSnapshot::rating).average().orElse(0.0D);
        Instant updatedAt = islandReviews.values().stream()
            .map(IslandReviewSnapshot::updatedAt)
            .max(Comparator.naturalOrder())
            .orElse(Instant.EPOCH);
        return new IslandReviewRankSnapshot(islandId, average, visibleReviews.size(), updatedAt);
    }

    private IslandReviewModerationSnapshot moderation(UUID islandId, UUID reviewerUuid) {
        return moderation.getOrDefault(islandId, Map.of()).getOrDefault(
            reviewerUuid,
            new IslandReviewModerationSnapshot(islandId, reviewerUuid, "VISIBLE", 0, "", null, Instant.EPOCH, "", Instant.EPOCH)
        );
    }
}
