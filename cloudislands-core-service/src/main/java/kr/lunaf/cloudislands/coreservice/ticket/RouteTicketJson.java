package kr.lunaf.cloudislands.coreservice.ticket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class RouteTicketJson {
    private RouteTicketJson() {
    }

    public static String storeSnapshot(Iterable<RouteTicket> tickets) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (tickets != null) {
            for (RouteTicket ticket : tickets) {
                values.add(ticketMap(ticket, false, false, true));
            }
        }
        return SimpleJson.stringify(Map.of("tickets", values));
    }

    public static String storeTicket(RouteTicket ticket) {
        return SimpleJson.stringify(ticketMap(ticket, false, false, true));
    }

    public static String routeResponse(RouteTicket ticket) {
        return SimpleJson.stringify(ticketMap(ticket, true, true, false));
    }

    public static String payload(Map<String, String> payload) {
        return SimpleJson.stringify(payloadMap(payload));
    }

    public static Map<String, String> parsePayload(String json) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        if (root.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            values.put(SimpleJson.text(entry.getKey()), SimpleJson.text(entry.getValue()));
        }
        return Map.copyOf(values);
    }

    private static LinkedHashMap<String, Object> ticketMap(RouteTicket ticket, boolean includeTargetLocalLocation, boolean flattenPayload, boolean includePayload) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("ticketId", ticket.ticketId());
        values.put("playerUuid", ticket.playerUuid());
        values.put("action", ticket.action());
        values.put("islandId", ticket.islandId());
        values.put("targetNode", ticket.targetNode());
        values.put("targetWorld", ticket.targetWorld());
        values.put("targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode()));
        values.put("state", ticket.state());
        values.put("expiresAt", ticket.expiresAt());
        values.put("nonce", ticket.nonce());
        if (includeTargetLocalLocation) {
            values.put("targetLocalLocation", targetLocalLocation(ticket.payload()));
        }
        if (flattenPayload) {
            for (Map.Entry<String, String> entry : ticket.payload().entrySet()) {
                if (!entry.getKey().equals("targetServerName")) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (includePayload) {
            values.put("payload", payloadMap(ticket.payload()));
        }
        return values;
    }

    private static LinkedHashMap<String, Object> targetLocalLocation(Map<String, String> payload) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("type", payload == null ? "ISLAND_HOME" : payload.getOrDefault("targetType", "ISLAND_HOME"));
        putIfPresent(values, payload, "homeName");
        putIfPresent(values, payload, "warpName");
        putIfPresent(values, payload, "localX");
        putIfPresent(values, payload, "localY");
        putIfPresent(values, payload, "localZ");
        putIfPresent(values, payload, "yaw");
        putIfPresent(values, payload, "pitch");
        return values;
    }

    private static LinkedHashMap<String, Object> payloadMap(Map<String, String> payload) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return values;
    }

    private static void putIfPresent(LinkedHashMap<String, Object> values, Map<String, String> payload, String key) {
        String value = payload == null ? null : payload.get(key);
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
