package kr.lunaf.cloudislands.coreservice.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class RouteSessionJson {
    private RouteSessionJson() {
    }

    public static String snapshot(Iterable<PlayerRouteSession> sessions) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (sessions != null) {
            for (PlayerRouteSession session : sessions) {
                values.add(sessionMap(session));
            }
        }
        return SimpleJson.stringify(Map.of("sessions", values));
    }

    public static String session(PlayerRouteSession session) {
        return SimpleJson.stringify(sessionMap(session));
    }

    private static LinkedHashMap<String, Object> sessionMap(PlayerRouteSession session) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("playerUuid", session.playerUuid());
        values.put("ticketId", session.ticketId());
        values.put("targetNode", session.targetNode());
        values.put("targetServerName", session.targetServerName());
        values.put("nonce", session.nonce());
        values.put("expiresAt", session.expiresAt());
        return values;
    }
}
