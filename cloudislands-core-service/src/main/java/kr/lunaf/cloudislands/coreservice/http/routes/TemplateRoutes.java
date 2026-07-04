package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;

public final class TemplateRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final IslandTemplateRepository templates;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public TemplateRoutes(IslandTemplateRepository templates, AuditLogger audit, GlobalEventPublisher events) {
        this.templates = templates;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/templates/list", exchange -> CoreHttpResponses.write(exchange, 200, templatesJson(templates.list())));
        registry.routePost("/v1/admin/templates/get", this::get);
        registry.routePost("/v1/admin/templates/upsert", this::upsert);
        registry.routePost("/v1/admin/templates/import-bundle", this::upsert);
        registry.routePost("/v1/admin/templates/verify-bundle", this::verifyBundle);
        registry.routePost("/v1/admin/templates/enable", this::enable);
        registry.routePost("/v1/admin/templates/disable", this::disable);
        registry.routePost("/v1/admin/templates/delete", this::delete);
        registry.routePost("/v1/admin/templates/reorder", this::reorder);
    }

    private void get(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        CoreHttpResponses.write(
            exchange,
            templates.find(templateId).isPresent() ? 200 : 404,
            templates.find(templateId).map(TemplateRoutes::templateJson).orElseGet(() -> ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found"))
        );
    }

    private void upsert(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        boolean enabled = JsonFields.bool(body, "enabled", true);
        if (enabled && migrationInputOnlyTemplate(templateId)) {
            audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_UPSERT_REJECTED", "TEMPLATE", templateId, Map.of("reason", "MIGRATION_INPUT_ONLY"));
            CoreHttpResponses.write(exchange, 409, migrationInputOnlyTemplateError());
            return;
        }
        IslandTemplateSnapshot snapshot = templates.upsert(templateFrom(body, templateId, enabled));
        audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_UPSERT", "TEMPLATE", snapshot.id(), Map.of("enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion(), "bundleStoragePath", snapshot.bundleStoragePath(), "bundleChecksum", snapshot.bundleChecksum()));
        events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", snapshot.id(), "enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion(), "operation", "UPSERT", "bundleStoragePath", snapshot.bundleStoragePath()));
        CoreHttpResponses.write(exchange, 202, templateJson(snapshot));
    }

    private void verifyBundle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        IslandTemplateSnapshot template = templates.find(templateId).orElse(null);
        if (template == null) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found"));
            return;
        }
        if (template.bundleStoragePath().isBlank()) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("TEMPLATE_BUNDLE_MISSING", "Island template has no bundle storage path"));
            return;
        }
        if (template.bundleChecksum().isBlank()) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("TEMPLATE_BUNDLE_CHECKSUM_MISSING", "Island template has no bundle checksum"));
            return;
        }
        CoreHttpResponses.write(exchange, 200, SimpleJson.stringify(Map.of("ok", true, "templateId", template.id(), "bundleStoragePath", template.bundleStoragePath(), "bundleChecksum", template.bundleChecksum(), "bundleSizeBytes", template.bundleSizeBytes())));
    }

    private void enable(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        if (migrationInputOnlyTemplate(templateId)) {
            audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_ENABLE_REJECTED", "TEMPLATE", templateId, Map.of("reason", "MIGRATION_INPUT_ONLY"));
            CoreHttpResponses.write(exchange, 409, migrationInputOnlyTemplateError());
            return;
        }
        setEnabled(exchange, templateId, true, "TEMPLATE_ENABLE", "ENABLE");
    }

    private void disable(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        setEnabled(exchange, templateId(body), false, "TEMPLATE_DISABLE", "DISABLE");
    }

    private void setEnabled(com.sun.net.httpserver.HttpExchange exchange, String templateId, boolean enabled, String auditAction, String operation) throws IOException {
        boolean changed = templates.setEnabled(templateId, enabled);
        audit.log(SYSTEM_ACTOR, "ADMIN", auditAction, "TEMPLATE", templateId, Map.of("changed", Boolean.toString(changed)));
        if (changed) {
            events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", templateId, "enabled", Boolean.toString(enabled), "operation", operation));
        }
        CoreHttpResponses.write(
            exchange,
            changed ? 202 : 404,
            changed ? templates.find(templateId).map(TemplateRoutes::templateJson).orElseGet(() -> ApiResponses.ok(true)) : ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found")
        );
    }

    private void delete(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        boolean confirm = JsonFields.bool(body, "confirm", false);
        if (!confirm) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("CONFIRM_REQUIRED", "Template delete requires confirm=true"));
            return;
        }
        boolean changed = templates.delete(templateId);
        audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_DELETE", "TEMPLATE", templateId, Map.of("changed", Boolean.toString(changed)));
        if (changed) {
            events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", templateId, "enabled", "false", "operation", "DELETE"));
        }
        CoreHttpResponses.write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found or cannot be deleted"));
    }

    private void reorder(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String templateId = templateId(body);
        int sortOrder = JsonFields.integer(body, "sortOrder", 0);
        boolean changed = templates.reorder(templateId, sortOrder);
        audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_REORDER", "TEMPLATE", templateId, Map.of("changed", Boolean.toString(changed), "sortOrder", Integer.toString(sortOrder)));
        if (changed) {
            events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", templateId, "operation", "REORDER", "sortOrder", Integer.toString(sortOrder)));
        }
        CoreHttpResponses.write(exchange, changed ? 202 : 404, changed ? templates.find(templateId).map(TemplateRoutes::templateJson).orElseGet(() -> ApiResponses.ok(true)) : ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found"));
    }

    private static String templateId(String body) {
        return JsonFields.text(body, "templateId", JsonFields.text(body, "id", "default"));
    }

    static String templatesJson(List<IslandTemplateSnapshot> templates) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (IslandTemplateSnapshot template : templates) {
            values.add(templateMap(template));
        }
        return SimpleJson.stringify(Map.of("templates", values));
    }

    static String templateJson(IslandTemplateSnapshot template) {
        return SimpleJson.stringify(templateMap(template));
    }

    private static Map<String, Object> templateMap(IslandTemplateSnapshot template) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", template.id());
        value.put("displayName", template.displayName());
        value.put("description", template.description());
        value.put("category", template.category());
        value.put("enabled", template.enabled());
        value.put("minNodeVersion", template.minNodeVersion());
        value.put("requiredPermission", template.requiredPermission());
        value.put("iconMaterial", template.iconMaterial());
        value.put("iconCustomModelData", template.iconCustomModelData());
        value.put("previewImageKey", template.previewImageKey());
        value.put("bundleStoragePath", template.bundleStoragePath());
        value.put("bundleChecksum", template.bundleChecksum());
        value.put("bundleSizeBytes", template.bundleSizeBytes());
        value.put("schemaVersion", template.schemaVersion());
        value.put("defaultIslandSize", template.defaultIslandSize());
        value.put("spawnWorldOffsetX", template.spawnWorldOffsetX());
        value.put("spawnWorldOffsetY", template.spawnWorldOffsetY());
        value.put("spawnWorldOffsetZ", template.spawnWorldOffsetZ());
        value.put("spawnYaw", template.spawnYaw());
        value.put("spawnPitch", template.spawnPitch());
        value.put("homeName", template.homeName());
        value.put("environmentPreset", template.environmentPreset());
        value.put("biomeKey", template.biomeKey());
        value.put("borderColor", template.borderColor());
        value.put("bankInitialBalance", template.bankInitialBalance());
        value.put("creationCost", template.creationCost());
        value.put("sortOrder", template.sortOrder());
        value.put("tags", template.tags());
        value.put("createdAt", template.createdAt().equals(java.time.Instant.EPOCH) ? "" : template.createdAt().toString());
        value.put("updatedAt", template.updatedAt().equals(java.time.Instant.EPOCH) ? "" : template.updatedAt().toString());
        return value;
    }

    private static IslandTemplateSnapshot templateFrom(String body, String templateId, boolean enabled) {
        return new IslandTemplateSnapshot(
            templateId,
            JsonFields.text(body, "displayName", templateId),
            JsonFields.text(body, "description", ""),
            JsonFields.text(body, "category", "default"),
            enabled,
            JsonFields.text(body, "minNodeVersion", ""),
            JsonFields.text(body, "requiredPermission", ""),
            JsonFields.text(body, "iconMaterial", "GRASS_BLOCK"),
            JsonFields.integer(body, "iconCustomModelData", 0),
            JsonFields.text(body, "previewImageKey", ""),
            JsonFields.text(body, "bundleStoragePath", JsonFields.text(body, "templateBundlePath", "")),
            JsonFields.text(body, "bundleChecksum", JsonFields.text(body, "templateBundleChecksum", "")),
            JsonFields.longValue(body, "bundleSizeBytes", 0L),
            JsonFields.integer(body, "schemaVersion", 3),
            JsonFields.integer(body, "defaultIslandSize", 300),
            JsonFields.decimal(body, "spawnWorldOffsetX", JsonFields.decimal(body, "localX", 0.5D)),
            JsonFields.decimal(body, "spawnWorldOffsetY", JsonFields.decimal(body, "localY", 100.0D)),
            JsonFields.decimal(body, "spawnWorldOffsetZ", JsonFields.decimal(body, "localZ", 0.5D)),
            (float) JsonFields.decimal(body, "spawnYaw", JsonFields.decimal(body, "yaw", 180.0D)),
            (float) JsonFields.decimal(body, "spawnPitch", JsonFields.decimal(body, "pitch", 0.0D)),
            JsonFields.text(body, "homeName", "default"),
            JsonFields.text(body, "environmentPreset", "normal"),
            JsonFields.text(body, "biomeKey", "minecraft:plains"),
            JsonFields.text(body, "borderColor", "BLUE"),
            JsonFields.text(body, "bankInitialBalance", "0"),
            JsonFields.text(body, "creationCost", "0"),
            JsonFields.integer(body, "sortOrder", 0),
            tags(JsonFields.text(body, "tags", "")),
            java.time.Instant.EPOCH,
            java.time.Instant.now()
        );
    }

    private static List<String> tags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .toList();
    }

    static boolean migrationInputOnlyTemplate(String templateId) {
        return "superiorskyblock2".equalsIgnoreCase(templateId == null ? "" : templateId.trim());
    }

    private static String migrationInputOnlyTemplateError() {
        return ApiResponses.error("TEMPLATE_MIGRATION_INPUT_ONLY", "This template is reserved for migration imports and cannot be enabled for normal island creation");
    }

}
