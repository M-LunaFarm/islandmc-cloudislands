package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys;

public final class PlayerProfileRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final PlayerProfileRepository playerProfiles;
    private final AuditLogger audit;
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;

    public PlayerProfileRoutes(PlayerProfileRepository playerProfiles, AuditLogger audit) {
        this(playerProfiles, null, null, audit);
    }

    public PlayerProfileRoutes(PlayerProfileRepository playerProfiles, IslandRepository islands, IslandMetadataRepository metadata, AuditLogger audit) {
        this.playerProfiles = playerProfiles;
        this.islands = islands;
        this.metadata = metadata;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/players/info", this::adminInfo);
        registry.routePost("/v1/players/info", this::info);
        registry.routePost("/v1/players/touch", this::touch);
        registry.routePost("/v1/players/locale", this::locale);
        registry.routePost("/v1/players/island-fly", this::islandFly);
        registry.routePost("/v1/players/world-border", this::worldBorder);
        registry.routePost("/v1/players/blocks-stacker", this::blocksStacker);
        registry.routePost("/v1/players/border-color", this::borderColor);
        registry.routePost("/v1/players/select-island/reserve", this::reserveIslandSelection);
        registry.routePost("/v1/players/select-island", this::selectIsland);
        registry.routePost("/v1/admin/players/setisland", this::setIsland);
        registry.routePost("/v1/admin/players/clearisland", this::clearIsland);
        registry.routePost("/v1/admin/players/setdisbands", this::setDisbands);
        registry.routePost("/v1/admin/players/adddisbands", this::addDisbands);
    }

    private void selectIsland(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        boolean owner = islands != null && islands.findById(islandId).map(island -> island.ownerUuid().equals(playerUuid)).orElse(false);
        boolean member = metadata != null && metadata.members(islandId).stream()
            .anyMatch(value -> value.playerUuid().equals(playerUuid) && CoreRoleKeys.memberRole(value.effectiveRoleKey()));
        if (!owner && !member) {
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("ISLAND_SELECTION_DENIED", "Player does not belong to the selected island"));
            return;
        }
        long suppliedRevision = JsonFields.longValue(body, "selectionRevision", 0L);
        long selectionRevision = suppliedRevision > 0L ? suppliedRevision : playerProfiles.reservePrimaryIslandSelection(playerUuid);
        var selected = playerProfiles.setPrimaryIslandIfSelectionCurrent(playerUuid, islandId, selectionRevision);
        if (selected.isEmpty()) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ISLAND_SELECTION_SUPERSEDED", "A newer primary island selection replaced this request"));
            return;
        }
        audit.log(playerUuid, "PLAYER", "PLAYER_SELECT_ISLAND", "ISLAND", islandId.toString(), Map.of(
            "islandId", islandId.toString(),
            "selectionRevision", Long.toString(selectionRevision)
        ));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(selected.orElseThrow()));
    }

    private void reserveIslandSelection(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        long selectionRevision = playerProfiles.reservePrimaryIslandSelection(playerUuid);
        CoreHttpResponses.write(exchange, 202, SimpleJson.stringify(Map.of("selectionRevision", selectionRevision)));
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

    private void islandFly(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        boolean enabled = JsonFields.bool(body, "enabled", false);
        audit.log(playerUuid, "PLAYER", "PLAYER_ISLAND_FLY_SET", "PLAYER", playerUuid.toString(), Map.of("enabled", Boolean.toString(enabled)));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setIslandFlyEnabled(playerUuid, enabled)));
    }

    private void worldBorder(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        boolean enabled = JsonFields.bool(body, "enabled", true);
        audit.log(playerUuid, "PLAYER", "PLAYER_WORLD_BORDER_SET", "PLAYER", playerUuid.toString(), Map.of("enabled", Boolean.toString(enabled)));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setWorldBorderEnabled(playerUuid, enabled)));
    }

    private void blocksStacker(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        boolean enabled = JsonFields.bool(body, "enabled", true);
        audit.log(playerUuid, "PLAYER", "PLAYER_BLOCKS_STACKER_SET", "PLAYER", playerUuid.toString(), Map.of("enabled", Boolean.toString(enabled)));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setBlocksStackerEnabled(playerUuid, enabled)));
    }

    private void borderColor(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String color = PlayerIslandProfile.normalizeBorderColor(JsonFields.text(body, "color", "blue"));
        audit.log(playerUuid, "PLAYER", "PLAYER_BORDER_COLOR_SET", "PLAYER", playerUuid.toString(), Map.of("color", color));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setBorderColor(playerUuid, color)));
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

    private void setDisbands(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        int value = JsonFields.integer(body, "value", 0);
        audit.log(EMPTY_UUID, "ADMIN", "PLAYER_SET_DISBANDS", "PLAYER", playerUuid.toString(), Map.of("value", Integer.toString(Math.max(0, value))));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.setDisbandsRemaining(playerUuid, value)));
    }

    private void addDisbands(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        int delta = JsonFields.integer(body, "delta", 0);
        audit.log(EMPTY_UUID, "ADMIN", "PLAYER_ADD_DISBANDS", "PLAYER", playerUuid.toString(), Map.of("delta", Integer.toString(delta)));
        CoreHttpResponses.write(exchange, 202, playerProfileJson(playerProfiles.addDisbandsRemaining(playerUuid, delta)));
    }

    static String playerProfileJson(PlayerIslandProfile profile) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("playerUuid", profile.playerUuid());
        values.put("lastName", profile.lastName());
        values.put("primaryIslandId", profile.primaryIslandId().orElse(null));
        values.put("lastSeenAt", profile.lastSeenAt());
        values.put("locale", profile.locale());
        values.put("disbandsRemaining", profile.disbandsRemaining());
        values.put("islandFlyEnabled", profile.islandFlyEnabled());
        values.put("worldBorderEnabled", profile.worldBorderEnabled());
        values.put("blocksStackerEnabled", profile.blocksStackerEnabled());
        values.put("borderColor", profile.borderColor());
        return SimpleJson.stringify(values);
    }
}
