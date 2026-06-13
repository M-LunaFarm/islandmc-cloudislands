package kr.lunaf.cloudislands.protocol.job;

import java.util.LinkedHashMap;
import java.util.Map;

public record IslandJobCompletionPayload(Map<String, String> fields) {
    public IslandJobCompletionPayload {
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    public static IslandJobCompletionPayload empty() {
        return new IslandJobCompletionPayload(Map.of());
    }

    public static IslandJobCompletionPayload activation(String worldName, int cellX, int cellZ, long schemaVersion, long fencingToken, String extractedRoot) {
        return empty()
            .with("worldName", worldName)
            .with("cellX", Integer.toString(cellX))
            .with("cellZ", Integer.toString(cellZ))
            .with("schemaVersion", Long.toString(schemaVersion))
            .with("fencingToken", Long.toString(fencingToken))
            .with("extractedRoot", extractedRoot);
    }

    public static IslandJobCompletionPayload snapshot(long snapshotNo, String reason, String checksum, long sizeBytes) {
        return empty()
            .with("snapshotNo", Long.toString(snapshotNo))
            .with("reason", reason)
            .with("checksum", checksum)
            .with("sizeBytes", Long.toString(sizeBytes));
    }

    public IslandJobCompletionPayload with(String key, String value) {
        if (key == null || key.isBlank()) {
            return this;
        }
        Map<String, String> copy = new LinkedHashMap<>(fields);
        copy.put(key, value == null ? "" : value);
        return new IslandJobCompletionPayload(copy);
    }

    public IslandJobCompletionPayload withSnapshot(long snapshotNo, String reason, String checksum, long sizeBytes) {
        if (snapshotNo <= 0L) {
            return this;
        }
        return with("snapshotNo", Long.toString(snapshotNo))
            .with("reason", reason)
            .with("checksum", checksum)
            .with("sizeBytes", Long.toString(sizeBytes));
    }

    public IslandJobCompletionPayload withPreMutationSnapshot(long snapshotNo, String reason, String checksum, long sizeBytes) {
        if (snapshotNo <= 0L) {
            return this;
        }
        return with("preMutationSnapshotNo", Long.toString(snapshotNo))
            .with("preMutationReason", reason)
            .with("preMutationChecksum", checksum)
            .with("preMutationSizeBytes", Long.toString(sizeBytes));
    }

    public Map<String, String> asMap() {
        return fields;
    }
}
