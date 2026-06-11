package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class JdkCoreApiClient implements CoreApiClient {
    private final URI baseUri;
    private final String authToken;
    private final HttpClient httpClient;

    public JdkCoreApiClient(URI baseUri, String authToken, Duration timeout) {
        this.baseUri = baseUri;
        this.authToken = authToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        return post("/v1/islands", "{\"playerUuid\":\"" + playerUuid + "\",\"templateId\":\"" + templateId + "\"}")
            .thenApply(body -> new CreateIslandResult(body.contains("\"accepted\":true"), text(body, "code", "FAILED"), null, RouteTicketJson.parseNested(body, "ticket")));
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return post("/v1/routes/home", "{\"playerUuid\":\"" + playerUuid + "\"}").thenApply(RouteTicketJson::parse);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return post("/v1/routes/visit", "{\"playerUuid\":\"" + visitorUuid + "\",\"islandId\":\"" + targetIslandId + "\"}").thenApply(RouteTicketJson::parse);
    }

    @Override
    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
        String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
        return post("/v1/routes/session", "{\"playerUuid\":\"" + ticket.playerUuid() + "\",\"ticketId\":\"" + ticket.ticketId() + "\",\"targetNode\":\"" + ticket.targetNode() + "\",\"targetServerName\":\"" + targetServerName + "\",\"nonce\":\"" + ticket.nonce() + "\",\"expiresAt\":\"" + ticket.expiresAt() + "\"}").thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId) {
        return post("/v1/routes/session/consume", "{\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + nodeId + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return post("/v1/routes/consume", "{\"ticketId\":\"" + ticketId + "\",\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + nodeId + "\",\"nonce\":\"" + nonce + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
    }

    @Override
    public CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request) {
        return post("/v1/nodes/heartbeat", "{\"nodeId\":\"" + request.nodeId() + "\",\"pool\":\"" + request.pool() + "\",\"velocityServerName\":\"" + request.velocityServerName() + "\",\"state\":\"" + request.state() + "\",\"players\":" + request.players() + ",\"activeIslands\":" + request.activeIslands() + ",\"mspt\":" + request.mspt() + ",\"activationQueue\":" + request.activationQueue() + ",\"heapUsedMb\":" + request.heapUsedMb() + ",\"heapMaxMb\":" + request.heapMaxMb() + "}").thenApply(_body -> null);
    }

    private CompletableFuture<String> post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
    }

    private static String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? fallback : json.substring(valueStart, end);
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static final class RouteSessionJson {
        static PlayerRouteSession parse(String json) {
            return new PlayerRouteSession(
                uuid(json, "playerUuid", new UUID(0L, 0L)),
                uuid(json, "ticketId", new UUID(0L, 0L)),
                text(json, "targetNode", ""),
                text(json, "targetServerName", ""),
                text(json, "nonce", ""),
                Instant.parse(text(json, "expiresAt", Instant.now().toString()))
            );
        }
    }

    private static final class RouteTicketJson {
        private RouteTicketJson() {}

        static RouteTicket parseNested(String json, String field) {
            String needle = "\"" + field + "\":";
            int start = json.indexOf(needle);
            if (start < 0) {
                return null;
            }
            int objectStart = json.indexOf('{', start + needle.length());
            if (objectStart < 0) {
                return null;
            }
            int depth = 0;
            for (int i = objectStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return parse(json.substring(objectStart, i + 1));
                    }
                }
            }
            return null;
        }

        static RouteTicket parse(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            UUID ticketId = uuid(json, "ticketId", UUID.randomUUID());
            UUID playerUuid = uuid(json, "playerUuid", new UUID(0L, 0L));
            UUID islandId = uuid(json, "islandId", new UUID(0L, 0L));
            RouteAction action = enumValue(RouteAction.class, text(json, "action", "HOME"), RouteAction.HOME);
            RouteTicketState state = enumValue(RouteTicketState.class, text(json, "state", "READY"), RouteTicketState.READY);
            String targetNode = text(json, "targetNode", "");
            String targetWorld = text(json, "targetWorld", "ci_shard_001");
            String nonce = text(json, "nonce", "");
            String serverName = text(json, "targetServerName", targetNode);
            Instant expiresAt = Instant.parse(text(json, "expiresAt", Instant.now().plusSeconds(30).toString()));
            return new RouteTicket(ticketId, playerUuid, action, islandId, targetNode, targetWorld, state, expiresAt, nonce, Map.of("targetServerName", serverName));
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
