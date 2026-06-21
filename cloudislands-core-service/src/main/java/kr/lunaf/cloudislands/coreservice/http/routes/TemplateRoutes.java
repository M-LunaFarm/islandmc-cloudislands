package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
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
        registry.route("/v1/admin/templates/list", exchange -> CoreHttpResponses.write(exchange, 200, templatesJson(templates.list())));
        registry.route("/v1/admin/templates/upsert", this::upsert);
        registry.route("/v1/admin/templates/enable", this::enable);
        registry.route("/v1/admin/templates/disable", this::disable);
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
        IslandTemplateSnapshot snapshot = templates.upsert(
            templateId,
            JsonFields.text(body, "displayName", templateId),
            enabled,
            JsonFields.text(body, "minNodeVersion", "")
        );
        audit.log(SYSTEM_ACTOR, "ADMIN", "TEMPLATE_UPSERT", "TEMPLATE", snapshot.id(), Map.of("enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion()));
        events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", snapshot.id(), "enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion(), "operation", "UPSERT"));
        CoreHttpResponses.write(exchange, 202, templateJson(snapshot));
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
        value.put("enabled", template.enabled());
        value.put("minNodeVersion", template.minNodeVersion());
        return value;
    }

    static boolean migrationInputOnlyTemplate(String templateId) {
        return "superiorskyblock2".equalsIgnoreCase(templateId == null ? "" : templateId.trim());
    }

    private static String migrationInputOnlyTemplateError() {
        return ApiResponses.error("TEMPLATE_MIGRATION_INPUT_ONLY", "This template is reserved for migration imports and cannot be enabled for normal island creation");
    }

}
