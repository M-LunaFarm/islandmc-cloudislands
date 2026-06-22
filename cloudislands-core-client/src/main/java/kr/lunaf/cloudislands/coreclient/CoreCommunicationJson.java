package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;

final class CoreCommunicationJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final Set<String> INTERNAL_LOG_PAYLOAD_KEYS = Set.of(
        "activenode",
        "activeworld",
        "cellx",
        "cellz",
        "fromnode",
        "nodeid",
        "originx",
        "originz",
        "sourcenode",
        "targetnode",
        "targetservername",
        "worldname"
    );

    private CoreCommunicationJson() {
    }

    static List<IslandLogRecord> records(String body) {
        return CoreJson.entries(body, "logs").stream()
            .map(CoreCommunicationJson::record)
            .filter(log -> !log.action().isBlank())
            .toList();
    }

    static ChatActionView chatAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new ChatActionView(CoreJson.accepted(root), CoreJson.code(root, successCode), CoreJson.text(root, "channel"), CoreJson.text(root, "message"));
    }

    static CoreGuiViews.LogEntryView view(IslandLogRecord log) {
        if (log == null) {
            return new CoreGuiViews.LogEntryView("", "", Map.of(), "");
        }
        return new CoreGuiViews.LogEntryView(
            log.actorUuid() == null || log.actorUuid().equals(EMPTY_UUID) ? "" : log.actorUuid().toString(),
            log.action(),
            log.payload() == null ? Map.of() : log.payload(),
            log.createdAt() == null || log.createdAt().equals(Instant.EPOCH) ? "" : log.createdAt().toString()
        );
    }

    private static IslandLogRecord record(Map<?, ?> values) {
        return new IslandLogRecord(
            uuid(CoreJson.text(values, "logId")),
            uuid(CoreJson.text(values, "islandId")),
            uuid(CoreJson.text(values, "actorUuid")),
            CoreJson.text(values, "action"),
            logPayload(CoreJson.objectValue(values, "payload")),
            instant(CoreJson.text(values, "createdAt"))
        );
    }

    private static Map<String, String> logPayload(Map<?, ?> payload) {
        Map<String, String> values = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<?, ?> entry : payload.entrySet()) {
                String key = CoreJson.textValue(entry.getKey());
                if (!key.isBlank() && !INTERNAL_LOG_PAYLOAD_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    values.put(key, CoreJson.textValue(entry.getValue()));
                }
            }
        }
        return Map.copyOf(values);
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
