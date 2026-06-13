package kr.lunaf.cloudislands.coreservice.snapshot;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public interface IslandSnapshotRepository {
    IslandSnapshotRecord record(UUID islandId, long snapshotNo, String storagePath, String reason, UUID createdBy, String checksum, long sizeBytes);
    List<IslandSnapshotRecord> list(UUID islandId, int limit);
    Optional<IslandSnapshotRecord> find(UUID islandId, long snapshotNo);
    int prune(UUID islandId, int keepLatest);
    int pruneRetaining(UUID islandId, Set<Long> retainedSnapshotNos);

    default int prune(UUID islandId, SnapshotRetentionPolicy policy) {
        SnapshotRetentionPolicy effectivePolicy = policy == null ? SnapshotRetentionPolicy.defaultPolicy() : policy.normalized();
        List<IslandSnapshotRecord> snapshots = list(islandId, Integer.MAX_VALUE).stream()
            .sorted(Comparator.comparingLong(IslandSnapshotRecord::snapshotNo).reversed())
            .toList();
        Set<Long> retainedSnapshotNos = retainedSnapshotNos(snapshots, effectivePolicy);
        return pruneRetaining(islandId, retainedSnapshotNos);
    }

    private static Set<Long> retainedSnapshotNos(List<IslandSnapshotRecord> snapshots, SnapshotRetentionPolicy policy) {
        Set<Long> retained = new LinkedHashSet<>();
        if (policy.keepDaily() == 0 && policy.keepWeekly() == 0 && policy.keepManual() == 0) {
            snapshots.stream()
                .limit(policy.keepHourly())
                .map(IslandSnapshotRecord::snapshotNo)
                .forEach(retained::add);
            return retained;
        }
        int manualKept = 0;
        for (IslandSnapshotRecord snapshot : snapshots) {
            if (isManualSnapshot(snapshot) && manualKept < policy.keepManual()) {
                retained.add(snapshot.snapshotNo());
                manualKept++;
            }
        }
        retainAutomaticByBucket(snapshots, retained, policy.keepHourly(), IslandSnapshotRepository::hourBucket);
        retainAutomaticByBucket(snapshots, retained, policy.keepDaily(), IslandSnapshotRepository::dayBucket);
        retainAutomaticByBucket(snapshots, retained, policy.keepWeekly(), IslandSnapshotRepository::weekBucket);
        return retained;
    }

    private static void retainAutomaticByBucket(List<IslandSnapshotRecord> snapshots, Set<Long> retained, int limit, Function<IslandSnapshotRecord, String> bucketKey) {
        if (limit <= 0) {
            return;
        }
        Set<String> retainedBuckets = new LinkedHashSet<>();
        int kept = 0;
        for (IslandSnapshotRecord snapshot : snapshots) {
            if (isManualSnapshot(snapshot) || retained.contains(snapshot.snapshotNo())) {
                continue;
            }
            String bucket = bucketKey.apply(snapshot);
            if (retainedBuckets.add(bucket)) {
                retained.add(snapshot.snapshotNo());
                kept++;
                if (kept >= limit) {
                    return;
                }
            }
        }
    }

    private static boolean isManualSnapshot(IslandSnapshotRecord snapshot) {
        String reason = snapshot.reason() == null ? "" : snapshot.reason().toUpperCase(java.util.Locale.ROOT);
        return reason.contains("MANUAL");
    }

    private static String hourBucket(IslandSnapshotRecord snapshot) {
        return createdAt(snapshot).atZone(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
    }

    private static String dayBucket(IslandSnapshotRecord snapshot) {
        return createdAt(snapshot).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static String weekBucket(IslandSnapshotRecord snapshot) {
        var date = createdAt(snapshot).atZone(ZoneOffset.UTC).toLocalDate();
        WeekFields weekFields = WeekFields.ISO;
        return date.get(weekFields.weekBasedYear()) + "-W" + date.get(weekFields.weekOfWeekBasedYear());
    }

    private static Instant createdAt(IslandSnapshotRecord snapshot) {
        return snapshot.createdAt() == null ? Instant.EPOCH : snapshot.createdAt();
    }
}
