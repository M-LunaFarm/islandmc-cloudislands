package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class RouteTicketService {
    private final NodeRegistry nodes;
    private final Duration readyTicketTtl;
    private final Duration preparingTicketTtl;

    public RouteTicketService(NodeRegistry nodes, Duration readyTicketTtl, Duration preparingTicketTtl) {
        this.nodes = nodes;
        this.readyTicketTtl = readyTicketTtl == null || readyTicketTtl.isNegative() || readyTicketTtl.isZero() ? Duration.ofSeconds(30) : readyTicketTtl;
        this.preparingTicketTtl = preparingTicketTtl == null || preparingTicketTtl.isNegative() || preparingTicketTtl.isZero() ? Duration.ofSeconds(120) : preparingTicketTtl;
    }

    public RouteTicket ticket(UUID playerUuid, UUID islandId, RouteAction action, Map<String, String> extraPayload, RouteTargetSelection target) {
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("targetServerName", targetServerName(target.node().nodeId()));
        mergePayload(payload, extraPayload);
        return new RouteTicket(
            UUID.randomUUID(),
            playerUuid,
            action,
            islandId,
            target.node().nodeId(),
            target.worldName(),
            target.state(),
            Instant.now().plus(target.state() == RouteTicketState.PREPARING ? preparingTicketTtl : readyTicketTtl),
            UUID.randomUUID().toString(),
            Map.copyOf(payload)
        );
    }

    public String targetServerName(String targetNode) {
        if (targetNode == null || targetNode.isBlank()) {
            return "";
        }
        return nodes.find(targetNode)
            .map(NodeLoad::velocityServerName)
            .filter(value -> value != null && !value.isBlank())
            .orElse(targetNode);
    }

    private void mergePayload(LinkedHashMap<String, String> payload, Map<String, String> extraPayload) {
        if (extraPayload == null || extraPayload.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : extraPayload.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            payload.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
    }
}
