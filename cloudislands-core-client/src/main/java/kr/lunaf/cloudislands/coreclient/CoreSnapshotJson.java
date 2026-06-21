package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreSnapshotJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private CoreSnapshotJson() {
    }

    static List<IslandSnapshotRecord> records(String body) {
        return CoreJson.entries(body).stream()
            .map(CoreSnapshotJson::record)
            .filter(snapshot -> snapshot.snapshotNo() > 0L)
            .toList();
    }

    static SnapshotActionView action(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new SnapshotActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
    }

    static CoreGuiViews.SnapshotView view(IslandSnapshotRecord snapshot) {
        if (snapshot == null) {
            return new CoreGuiViews.SnapshotView(0L, "", 0L, "", "", "");
        }
        return new CoreGuiViews.SnapshotView(
            snapshot.snapshotNo(),
            snapshot.reason(),
            snapshot.sizeBytes(),
            snapshot.createdAt() == null || snapshot.createdAt().equals(Instant.EPOCH) ? "" : snapshot.createdAt().toString(),
            snapshot.checksum(),
            snapshot.storagePath()
        );
    }

    private static IslandSnapshotRecord record(Map<?, ?> values) {
        return new IslandSnapshotRecord(
            uuid(text(values, "snapshotId")),
            uuid(text(values, "islandId")),
            SimpleJson.number(values.get("snapshotNo")),
            text(values, "storagePath"),
            text(values, "reason"),
            uuid(text(values, "createdBy")),
            text(values, "checksum"),
            SimpleJson.number(values.get("sizeBytes")),
            instant(text(values, "createdAt"))
        );
    }

    private static String text(Map<?, ?> values, String key) {
        return SimpleJson.text(values.get(key));
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? EMPTY_UUID : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return EMPTY_UUID;
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }
}
