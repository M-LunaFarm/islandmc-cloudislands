package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class IslandCommunicationRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandLogRepository islandLogs;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final PlayerProfileRepository playerProfiles;
    private final GlobalEventPublisher events;

    public IslandCommunicationRoutes(
            IslandLogRepository islandLogs,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            PlayerProfileRepository playerProfiles,
            GlobalEventPublisher events) {
        this.islandLogs = islandLogs;
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.playerProfiles = playerProfiles;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/logs", this::logs);
        registry.route("/v1/islands/chat", this::chat);
    }

    private void logs(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, islandLogsJson(islandLogs.list(JsonFields.uuid(body, "islandId", EMPTY_UUID), JsonFields.integer(body, "limit", 30))));
    }

    private void chat(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String channel = JsonFields.text(body, "channel", "ISLAND").toUpperCase();
        String message = JsonFields.text(body, "message", "");
        if (!requireMember(exchange, islandId, actorUuid)) {
            return;
        }
        if (message.isBlank()) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("EMPTY_CHAT_MESSAGE", "Chat message is empty"));
            return;
        }
        String normalizedChannel = channel.equals("TEAM") ? "TEAM" : "ISLAND";
        String actorName = playerProfiles.find(actorUuid).lastName();
        islandLogs.append(islandId, actorUuid, "ISLAND_CHAT", Map.of("channel", normalizedChannel, "message", message));
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("islandId", islandId.toString());
        payload.put("actorUuid", actorUuid.toString());
        payload.put("actorName", actorName == null || actorName.isBlank() ? actorUuid.toString() : actorName);
        payload.put("channel", normalizedChannel);
        payload.put("message", message);
        if (normalizedChannel.equals("TEAM")) {
            payload.put("recipients", String.join(",", metadataRepository.members(islandId).stream().map(member -> member.playerUuid().toString()).toList()));
        }
        events.publish(CloudIslandEventType.ISLAND_CHAT_SENT.name(), payload);
        CoreHttpResponses.write(exchange, 202, "{\"accepted\":true,\"channel\":\"" + normalizedChannel + "\",\"message\":\"" + escape(message) + "\"}");
    }

    private boolean requireMember(HttpExchange exchange, UUID islandId, UUID actorUuid) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean member = metadataRepository.members(islandId).stream()
            .anyMatch(record -> record.playerUuid().equals(actorUuid) && record.role() != IslandRole.VISITOR && record.role() != IslandRole.BANNED);
        if (owner || member) {
            return true;
        }
        CoreHttpResponses.write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island member permission is required"));
        return false;
    }

    static String islandLogsJson(java.util.List<IslandLogRecord> logs) {
        StringBuilder builder = new StringBuilder("{\"logs\":[");
        boolean first = true;
        for (IslandLogRecord log : logs) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"logId\":\"").append(log.logId()).append("\",")
                .append("\"islandId\":\"").append(log.islandId()).append("\",")
                .append("\"actorUuid\":\"").append(log.actorUuid()).append("\",")
                .append("\"action\":\"").append(escape(log.action())).append("\",")
                .append("\"payload\":").append(stringMapJson(log.payload())).append(',')
                .append("\"createdAt\":\"").append(log.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String stringMapJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (payload == null || payload.isEmpty()) {
            return builder.append("}").toString();
        }
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}").toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
