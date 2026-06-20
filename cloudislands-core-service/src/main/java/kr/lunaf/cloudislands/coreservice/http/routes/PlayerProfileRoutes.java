package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;

public final class PlayerProfileRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final PlayerProfileRepository playerProfiles;
    private final AuditLogger audit;

    public PlayerProfileRoutes(PlayerProfileRepository playerProfiles, AuditLogger audit) {
        this.playerProfiles = playerProfiles;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/players/info", this::adminInfo);
        registry.route("/v1/players/info", this::info);
        registry.route("/v1/players/touch", this::touch);
        registry.route("/v1/players/locale", this::locale);
        registry.route("/v1/admin/players/setisland", this::setIsland);
        registry.route("/v1/admin/players/clearisland", this::clearIsland);
    }

    private void adminInfo(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, playerProfileJson(playerProfiles.find(JsonFields.uuid(body, "playerUuid", EMPTY_UUID))));
    }

    private void info(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String lastName = JsonFields.text(body, "lastName", "");
        java.util.Optional<PlayerIslandProfile> profile = playerUuid.equals(EMPTY_UUID)
            ? playerProfiles.findByLastName(lastName)
            : java.util.Optional.of(playerProfiles.find(playerUuid));
        CoreHttpResponses.write(exchange, profile.isPresent() ? 200 : 404, profile.map(PlayerProfileRoutes::playerProfileJson).orElseGet(() -> ApiResponses.error("PLAYER_NOT_FOUND", "Player was not found")));
    }

    private void touch(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String lastName = JsonFields.text(body, "lastName", "");
        String locale = JsonFields.text(body, "locale", "");
        PlayerIslandProfile profile = locale.isBlank()
            ? playerProfiles.touch(playerUuid, lastName)
            : playerProfiles.touch(playerUuid, lastName, locale);
        CoreHttpResponses.write(exchange, 202, playerProfileJson(profile));
    }

    private void locale(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String locale = JsonFields.text(body, "locale", "");
        audit.log(playerUuid, "PLAYER", "PLAYER_LOCALE_SET", "PLAYER", playerUuid.toString(), Map.of("locale", PlayerIslandProfile.normalizeLocale(locale)));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setLocale(playerUuid, locale)));
    }

    private void setIsland(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        audit.log(EMPTY_UUID, "ADMIN", "PLAYER_SET_ISLAND", "PLAYER", playerUuid.toString(), Map.of("islandId", islandId.toString()));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setPrimaryIsland(playerUuid, islandId)));
    }

    private void clearIsland(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        audit.log(EMPTY_UUID, "ADMIN", "PLAYER_CLEAR_ISLAND", "PLAYER", playerUuid.toString(), Map.of());
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.clearPrimaryIsland(playerUuid)));
    }

    static String playerProfileJson(PlayerIslandProfile profile) {
        return "{\"playerUuid\":\"" + profile.playerUuid()
            + "\",\"lastName\":\"" + escape(profile.lastName())
            + "\",\"primaryIslandId\":" + profile.primaryIslandId().map(value -> "\"" + value + "\"").orElse("null")
            + ",\"lastSeenAt\":\"" + profile.lastSeenAt()
            + "\",\"locale\":\"" + escape(profile.locale())
            + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
