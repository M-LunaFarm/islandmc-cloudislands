package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

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
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return post("/v1/routes/home", "{\"playerUuid\":\"" + playerUuid + "\"}").thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return post("/v1/routes/visit", "{\"playerUuid\":\"" + visitorUuid + "\",\"islandId\":\"" + targetIslandId + "\"}").thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return post("/v1/routes/consume", "{\"ticketId\":\"" + ticketId + "\",\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + nodeId + "\",\"nonce\":\"" + nonce + "\"}").thenApply(_body -> Optional.empty());
    }

    @Override
    public CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request) {
        return post("/v1/nodes/heartbeat", "{\"nodeId\":\"" + request.nodeId() + "\"}").thenApply(_body -> null);
    }

    private CompletableFuture<String> post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }
}
