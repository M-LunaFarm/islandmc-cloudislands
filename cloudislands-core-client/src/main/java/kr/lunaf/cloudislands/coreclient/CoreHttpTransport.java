package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class CoreHttpTransport {
    private final URI baseUri;
    private final String authToken;
    private final String adminToken;
    private final String nodeId;
    private final Duration timeout;
    private final HttpClient httpClient;

    CoreHttpTransport(URI baseUri, String authToken, String adminToken, Duration timeout) {
        this(baseUri, authToken, adminToken, "", timeout);
    }

    CoreHttpTransport(URI baseUri, String authToken, String adminToken, String nodeId, Duration timeout) {
        this.baseUri = baseUri;
        this.authToken = authToken == null ? "" : authToken;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.nodeId = nodeId == null ? "" : nodeId.trim();
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    CompletableFuture<CoreResponseBody> post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addNodeHeader(builder);
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        return send(builder.build()).thenApply(response -> response.responseBody(response.successBody()));
    }

    CompletableFuture<CoreResponseBody> get(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .GET();
        addNodeHeader(builder);
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        return send(builder.build()).thenApply(response -> response.responseBody(response.successBody()));
    }

    CompletableFuture<CoreResponseBody> postWithResultBody(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addNodeHeader(builder);
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        return send(builder.build()).thenApply(response -> response.responseBody(response.resultBody()));
    }

    CompletableFuture<CoreResponseBody> deleteWithResultBody(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .DELETE();
        addNodeHeader(builder);
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        return send(builder.build()).thenApply(response -> response.responseBody(response.resultBody()));
    }

    private CompletableFuture<CoreHttpResponse> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> new CoreHttpResponse(response.statusCode(), response.body()));
    }

    private boolean adminProtected(String path) {
        return path.startsWith("/v1/admin")
            || path.equals("/v1/audit")
            || path.equals("/v1/events")
            || path.equals("/metrics")
            || path.equals("/v1/jobs")
            || path.equals("/v1/jobs/claim")
            || path.equals("/v1/jobs/complete")
            || path.equals("/v1/jobs/fail")
            || path.equals("/v1/jobs/recover");
    }

    private void addAdminHeaders(HttpRequest.Builder builder, String path) {
        if (adminToken.isBlank() || !adminProtected(path)) {
            return;
        }
        builder.header("X-CloudIslands-Admin-Token", adminToken);
        String permission = adminPermission(path);
        if (!permission.isBlank()) {
            builder.header("X-CloudIslands-Admin-Permissions", permission);
        }
    }

    private void addNodeHeader(HttpRequest.Builder builder) {
        if (!nodeId.isBlank()) {
            builder.header("X-CloudIslands-Node-Id", nodeId);
        }
    }

    private String adminPermission(String path) {
        if (path.equals("/v1/jobs") || path.equals("/v1/jobs/claim") || path.equals("/v1/jobs/complete") || path.equals("/v1/jobs/fail") || path.equals("/v1/jobs/recover")) {
            return "JOB_MANAGE";
        }
        if (path.equals("/v1/audit") || path.equals("/v1/admin/audit") || path.equals("/v1/admin/audit/list") || path.equals("/v1/events") || path.equals("/metrics")) {
            return "AUDIT_READ";
        }
        if (path.equals("/v1/admin/cache/clear") || path.equals("/v1/admin/reload")) {
            return "CACHE_CLEAR";
        }
        if (path.startsWith("/v1/admin/migrations/")) {
            return "MIGRATION_MANAGE";
        }
        if (path.startsWith("/v1/admin/players/")) {
            return "PLAYER_MANAGE";
        }
        if (path.startsWith("/v1/admin/templates/")) {
            return "TEMPLATE_MANAGE";
        }
        if (path.startsWith("/v1/admin/routes/")) {
            return "ROUTE_MANAGE";
        }
        if (path.startsWith("/v1/admin/block-values")) {
            return "ECONOMY_MANAGE";
        }
        if (path.startsWith("/v1/admin/nodes/")) {
            if (path.endsWith("/drain") || path.endsWith("/sweep")) {
                return "NODE_DRAIN";
            }
            if (path.endsWith("/undrain")) {
                return "NODE_UNDRAIN";
            }
            if (path.endsWith("/kickall")) {
                return "NODE_KICK";
            }
            if (path.endsWith("/shutdown-safe")) {
                return "NODE_SHUTDOWN";
            }
            return "AUDIT_READ";
        }
        if (path.startsWith("/v1/admin/islands/")) {
            if (path.endsWith("/activate")) {
                return "ISLAND_ACTIVATE";
            }
            if (path.endsWith("/deactivate")) {
                return "ISLAND_DEACTIVATE";
            }
            if (path.endsWith("/migrate")) {
                return "ISLAND_MIGRATE";
            }
            if (path.endsWith("/snapshot")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/save")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/restore")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/rollback")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/quarantine")) {
                return "ISLAND_QUARANTINE";
            }
            if (path.endsWith("/delete")) {
                return "ISLAND_DELETE";
            }
            if (path.endsWith("/repair")) {
                return "ISLAND_REPAIR";
            }
            if (path.endsWith("/tp")) {
                return "ISLAND_TELEPORT";
            }
            return "AUDIT_READ";
        }
        return path.startsWith("/v1/admin") ? "AUDIT_READ" : "";
    }
}
