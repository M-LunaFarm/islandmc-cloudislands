package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class IslandSnapshotRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandSnapshotRepository snapshots;
    private final IslandRuntimeRepository runtimes;
    private final SnapshotRetentionPolicy retentionPolicy;
    private final GlobalEventPublisher events;

    public IslandSnapshotRoutes(IslandSnapshotRepository snapshots, IslandRuntimeRepository runtimes, SnapshotRetentionPolicy retentionPolicy, GlobalEventPublisher events) {
        this.snapshots = snapshots;
        this.runtimes = runtimes;
        this.retentionPolicy = retentionPolicy;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/snapshots", this::list);
        registry.route("/v1/islands/snapshots/record", this::record);
    }

    private void list(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, snapshotsJson(snapshots.list(JsonFields.uuid(body, "islandId", EMPTY_UUID), JsonFields.integer(body, "limit", 20))));
    }

    private void record(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
        if (snapshotNo <= 0L) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("INVALID_SNAPSHOT", "Snapshot number is required"));
            return;
        }
        String storagePath = JsonFields.text(body, "storagePath", defaultStoragePath(islandId, snapshotNo));
        String reason = JsonFields.text(body, "reason", "AUTO");
        String checksum = JsonFields.text(body, "checksum", "");
        long sizeBytes = JsonFields.longValue(body, "sizeBytes", 0L);
        String nodeId = JsonFields.text(body, "nodeId", "");
        long fencingToken = JsonFields.longValue(body, "fencingToken", 0L);
        var runtime = runtimes.find(islandId).orElse(null);
        if (runtime == null || runtime.activeNode() == null || !runtime.activeNode().equals(nodeId)) {
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("SNAPSHOT_NODE_MISMATCH", "Snapshot must be recorded by the active island node"));
            return;
        }
        if (fencingToken <= 0L || runtime.fencingToken() != fencingToken) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("STALE_FENCING_TOKEN", "Snapshot fencing token must match the active island runtime"));
            return;
        }
        int pruned = recordSnapshotAndPublish(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId, fencingToken);
        CoreHttpResponses.write(exchange, 202, "{\"accepted\":true,\"snapshotNo\":" + snapshotNo + ",\"storagePath\":\"" + escape(storagePath) + "\",\"checksum\":\"" + escape(checksum) + "\",\"sizeBytes\":" + sizeBytes + ",\"fencingToken\":" + fencingToken + ",\"pruned\":" + pruned + "}");
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        snapshots.record(islandId, snapshotNo, storagePath, reason, null, checksum, sizeBytes);
        int pruned = snapshots.prune(islandId, retentionPolicy);
        events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), snapshotEventFields(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId, fencingToken, pruned));
        return pruned;
    }

    static Map<String, String> snapshotEventFields(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken, int pruned) {
        return Map.of(
            "islandId", islandId.toString(),
            "snapshotNo", Long.toString(snapshotNo),
            "reason", reason == null ? "" : reason,
            "storagePath", storagePath == null ? "" : storagePath,
            "checksum", checksum == null ? "" : checksum,
            "sizeBytes", Long.toString(sizeBytes),
            "nodeId", nodeId == null ? "" : nodeId,
            "fencingToken", Long.toString(fencingToken),
            "pruned", Integer.toString(pruned)
        );
    }

    static String snapshotsJson(java.util.List<IslandSnapshotRecord> snapshots) {
        StringBuilder builder = new StringBuilder("{\"snapshots\":[");
        boolean first = true;
        for (IslandSnapshotRecord snapshot : snapshots) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"snapshotId\":\"").append(snapshot.snapshotId()).append("\",")
                .append("\"islandId\":\"").append(snapshot.islandId()).append("\",")
                .append("\"snapshotNo\":").append(snapshot.snapshotNo()).append(',')
                .append("\"storagePath\":\"").append(escape(snapshot.storagePath())).append("\",")
                .append("\"reason\":\"").append(escape(snapshot.reason())).append("\",")
                .append("\"createdBy\":\"").append(snapshot.createdBy()).append("\",")
                .append("\"checksum\":\"").append(escape(snapshot.checksum())).append("\",")
                .append("\"sizeBytes\":").append(snapshot.sizeBytes()).append(',')
                .append("\"createdAt\":\"").append(snapshot.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String defaultStoragePath(UUID islandId, long snapshotNo) {
        return "islands/" + islandId + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
