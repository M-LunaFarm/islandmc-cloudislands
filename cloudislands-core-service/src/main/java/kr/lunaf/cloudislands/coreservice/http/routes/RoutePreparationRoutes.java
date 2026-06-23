package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.RoutePreparationResult;
import kr.lunaf.cloudislands.coreservice.RoutingOrchestrator;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class RoutePreparationRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final RoutingOrchestrator routing;

    public RoutePreparationRoutes(RoutingOrchestrator routing) {
        this.routing = routing;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/routes/home", this::home);
        registry.routePost("/v1/routes/visit", this::visit);
        registry.routePost("/v1/routes/random", this::random);
        registry.routePost("/v1/routes/warp", this::warp);
        registry.routePost("/v1/routes/migration-return", this::migrationReturn);
        registry.routePost("/v1/admin/islands/tp", this::adminTeleport);
    }

    private void home(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        routeResult(exchange, routing.prepareHomeRoute(JsonFields.uuid(body, "playerUuid", EMPTY_UUID), JsonFields.text(body, "homeName", "default")));
    }

    private void visit(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String islandName = JsonFields.text(body, "islandName", "");
        if (!islandName.isBlank()) {
            routeResult(exchange, routing.prepareVisitRouteByName(playerUuid, islandName));
            return;
        }
        UUID ownerUuid = JsonFields.uuid(body, "ownerUuid", EMPTY_UUID);
        if (!ownerUuid.equals(EMPTY_UUID)) {
            routeResult(exchange, routing.prepareVisitRouteByOwner(playerUuid, ownerUuid));
            return;
        }
        routeResult(exchange, routing.prepareVisitRoute(playerUuid, JsonFields.uuid(body, "islandId", EMPTY_UUID)));
    }

    private void random(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        routeResult(exchange, routing.prepareRandomVisitRoute(JsonFields.uuid(body, "playerUuid", EMPTY_UUID)));
    }

    private void warp(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        routeResult(exchange, routing.prepareWarpRoute(JsonFields.uuid(body, "playerUuid", EMPTY_UUID), JsonFields.uuid(body, "islandId", EMPTY_UUID), JsonFields.text(body, "warpName", "default")));
    }

    private void migrationReturn(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("localX", Double.toString(JsonFields.decimal(body, "localX", 0.5D)));
        payload.put("localY", Double.toString(JsonFields.decimal(body, "localY", 100.0D)));
        payload.put("localZ", Double.toString(JsonFields.decimal(body, "localZ", 0.5D)));
        payload.put("yaw", Float.toString((float) JsonFields.decimal(body, "yaw", 180.0D)));
        payload.put("pitch", Float.toString((float) JsonFields.decimal(body, "pitch", 0.0D)));
        routeResult(exchange, routing.prepareMigrationReturnRoute(
            JsonFields.uuid(body, "playerUuid", EMPTY_UUID),
            JsonFields.uuid(body, "islandId", EMPTY_UUID),
            JsonFields.text(body, "targetNode", ""),
            payload
        ));
    }

    private void adminTeleport(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        routeResult(exchange, routing.prepareAdminTeleportRoute(JsonFields.uuid(body, "playerUuid", EMPTY_UUID), JsonFields.uuid(body, "islandId", EMPTY_UUID)));
    }

    static void routeResult(com.sun.net.httpserver.HttpExchange exchange, RoutePreparationResult result) throws IOException {
        CoreHttpResponses.write(exchange, result.status(), result.body());
    }
}
