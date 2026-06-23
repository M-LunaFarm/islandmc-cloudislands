package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class AddonRoutes implements RouteGroup {
    private final AddonStateRepository addonStates;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public AddonRoutes(AddonStateRepository addonStates, AuditLogger audit, GlobalEventPublisher events) {
        this.addonStates = addonStates;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/addons/state/summary", exchange -> CoreHttpResponses.write(exchange, 200, addonStateSummaryJson(addonStates.globalStateCounts(), addonStates.islandStateCounts())));
        registry.routePost("/v1/addons/state", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                CoreHttpResponses.write(exchange, 200, addonStateJson(addonStates.list(addonId)));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/set", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String key = JsonFields.text(body, "key", "");
            String value = JsonFields.text(body, "value", "");
            if (addonId.isBlank() || key.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.put(addonId, key, value);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_SET", "ADDON", addonId, Map.of("key", key));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "SET", "key", key));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/bulk", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.bulkSave(addonId, stateValues);
                String operation = tables.isEmpty() ? "BULK_SET" : "BULK_SAVE";
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_" + operation, "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", operation, "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/save", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.bulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.GLOBAL_BULK_SAVE_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.GLOBAL_BULK_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/table/bulk", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSave(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkLoadRequest.GLOBAL_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                Map<String, String> values = addonStates.tableKeyValueBulkLoad(addonId, table);
                CoreHttpResponses.write(exchange, 200, addonStateJson(values));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                Map<String, String> values = addonStates.table(addonId, table);
                CoreHttpResponses.write(exchange, 200, addonStateJson(values));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || (table.isBlank() && tables.isEmpty())) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table or tables are required"));
                return;
            }
            try {
                String safeTable = table.isBlank() ? "" : safeTableName(table);
                Map<String, String> tableValues = safeTable.isBlank() ? tableKeyValueBulkStateValues(Map.of(), tables, "") : tableStateValues(safeTable, values);
                int tableCount = safeTable.isBlank() ? tables.size() : 1;
                Map<String, String> state = safeTable.isBlank()
                    ? addonStates.tableBulk(addonId, tables)
                    : addonStates.tableKeyValueBulkSave(addonId, Map.of(), Map.of(safeTable, values));
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_BULK_SET", "ADDON", addonId, Map.of("table", safeTable, "keys", Integer.toString(tableValues.size()), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_BULK_SET", "table", safeTable, "keys", Integer.toString(tableValues.size()), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/table/replace", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.replacePrefix(addonId, prefix, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_REPLACE", "ADDON", addonId, Map.of("table", safeTable, "keys", Integer.toString(tableValues.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_REPLACE", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/table/clear", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> updated = addonStates.removePrefix(addonId, prefix);
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_CLEAR", "ADDON", addonId, Map.of("table", safeTable));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "TABLE_CLEAR", "table", safeTable));
                CoreHttpResponses.write(exchange, 202, addonStateJson(updated));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/remove", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String key = JsonFields.text(body, "key", "");
            if (addonId.isBlank() || key.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.remove(addonId, key);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_REMOVE", "ADDON", addonId, Map.of("key", key));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "REMOVE", "key", key));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/state/clear", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            if (addonId.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                addonStates.clear(addonId);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_CLEAR", "ADDON", addonId, Map.of());
                publishAddonStateChanged(events, Map.of("addonId", addonId, "operation", "CLEAR"));
                CoreHttpResponses.write(exchange, 202, addonStateJson(Map.of()));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                CoreHttpResponses.write(exchange, 200, addonStateJson(addonStates.listIsland(addonId, islandId)));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/set", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String key = JsonFields.text(body, "key", "");
            String value = JsonFields.text(body, "value", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || key.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.putIsland(addonId, islandId, key, value);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_SET", "ADDON", addonId, Map.of("islandId", islandId.toString(), "key", key));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "SET", "key", key));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/bulk", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.bulkSaveIsland(addonId, islandId, stateValues);
                String operation = tables.isEmpty() ? "BULK_SET" : "BULK_SAVE";
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_" + operation, "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", operation, "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/save", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.bulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.ISLAND_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.ISLAND_BULK_SAVE_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.ISLAND_BULK_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = tableKeyValueBulkStateValues(values, tables, table);
                int tableKeys = tableKeyValueBulkTableKeyCount(values, tables, table);
                int tableCount = tableKeyValueBulkTableCount(values, tables, table);
                Map<String, String> state = addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(rootValueKeyCount(values, table)), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkLoadRequest.ISLAND_ENDPOINT, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                Map<String, String> values = addonStates.tableKeyValueBulkLoadIsland(addonId, islandId, table);
                CoreHttpResponses.write(exchange, 200, addonStateJson(values));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost(AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS, exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                Map<String, String> values = addonStates.tableIsland(addonId, islandId, table);
                CoreHttpResponses.write(exchange, 200, addonStateJson(values));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/table/bulk", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || (table.isBlank() && tables.isEmpty())) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table or tables are required"));
                return;
            }
            try {
                String safeTable = table.isBlank() ? "" : safeTableName(table);
                Map<String, String> tableValues = safeTable.isBlank() ? tableKeyValueBulkStateValues(Map.of(), tables, "") : tableStateValues(safeTable, values);
                int tableCount = safeTable.isBlank() ? tables.size() : 1;
                Map<String, String> state = safeTable.isBlank()
                    ? addonStates.tableBulkIsland(addonId, islandId, tables)
                    : addonStates.tableKeyValueBulkSaveIsland(addonId, islandId, Map.of(), Map.of(safeTable, values));
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_BULK_SET", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable, "keys", Integer.toString(tableValues.size()), "tables", Integer.toString(tableCount)));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_BULK_SET", "table", safeTable, "keys", Integer.toString(tableValues.size()), "tables", Integer.toString(tableCount)));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/table/replace", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.replaceIslandPrefix(addonId, islandId, prefix, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_REPLACE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable, "keys", Integer.toString(tableValues.size())));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_REPLACE", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/table/clear", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> updated = addonStates.removeIslandPrefix(addonId, islandId, prefix);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_CLEAR", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_CLEAR", "table", safeTable));
                CoreHttpResponses.write(exchange, 202, addonStateJson(updated));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/remove", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String key = JsonFields.text(body, "key", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || key.isBlank()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.removeIsland(addonId, islandId, key);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_REMOVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "key", key));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "REMOVE", "key", key));
                CoreHttpResponses.write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        registry.routePost("/v1/addons/islands/state/clear", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                addonStates.clearIsland(addonId, islandId);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_CLEAR", "ADDON", addonId, Map.of("islandId", islandId.toString()));
                publishAddonStateChanged(events, Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "CLEAR"));
                CoreHttpResponses.write(exchange, 202, addonStateJson(Map.of()));
            } catch (IllegalArgumentException exception) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
    }

    private static Map<String, String> tableStateValues(String table, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        String safeTable = safeBulkTableName(table);
        if (safeTable.isBlank()) {
            return Map.of();
        }
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            String safeKey = safeBulkTableKey(key);
            if (!safeKey.isBlank() && tableStateKeyLength(safeTable, safeKey) <= AddonStateRepository.MAX_KEY_LENGTH) {
                state.put(tableStateKey(safeTable, safeKey), safeBulkValue(value));
            }
        });
        return Map.copyOf(state);
    }

    private static Map<String, String> tableKeyValueBulkStateValues(String valuesTable, Map<String, String> values, Map<String, Map<String, String>> tables) {
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        if (valuesTable == null || valuesTable.isBlank()) {
            if (values != null) {
                values.forEach((key, value) -> {
                    String safeKey = safeBulkRootKey(key);
                    if (!safeKey.isBlank()) {
                        state.put(safeKey, safeBulkValue(value));
                    }
                });
            }
        } else {
            state.putAll(tableStateValues(valuesTable, values));
        }
        if (tables != null) {
            tables.forEach((table, tableValues) -> state.putAll(tableStateValues(table, tableValues)));
        }
        return Map.copyOf(state);
    }

    private static Map<String, String> tableKeyValueBulkStateValues(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        return tableKeyValueBulkStateValues(valuesTable, values, tables);
    }

    private static int tableKeyValueBulkTableKeyCount(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        int count = tableKeyCount(tables);
        if (valuesTable != null && !valuesTable.isBlank()) {
            count += tableStateValues(valuesTable, values).size();
        }
        return count;
    }

    private static int tableKeyValueBulkTableCount(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        java.util.LinkedHashSet<String> tableNames = new java.util.LinkedHashSet<>();
        if (tables != null) {
            tables.keySet().forEach(table -> {
                Map<String, String> tableValues = tables.get(table);
                if (tableValues != null && !tableValues.isEmpty() && table != null && !table.isBlank()
                    && !tableStateValues(table, tableValues).isEmpty()) {
                    tableNames.add(safeTableName(table));
                }
            });
        }
        if (valuesTable != null && !valuesTable.isBlank() && !tableStateValues(valuesTable, values).isEmpty()) {
            tableNames.add(safeTableName(valuesTable));
        }
        return tableNames.size();
    }

    private static int rootValueKeyCount(Map<String, String> values, String valuesTable) {
        if (valuesTable != null && !valuesTable.isBlank()) {
            return 0;
        }
        return values == null ? 0 : values.size();
    }

    private static int tableKeyCount(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        return tables.entrySet().stream()
            .mapToInt(entry -> tableStateValues(entry.getKey(), entry.getValue()).size())
            .sum();
    }

    private static String tableStatePrefix(String table) {
        return AddonStateRepository.TABLE_STATE_KEY_PREFIX + safeTableName(table) + "/";
    }

    private static String tableStateKey(String table, String key) {
        String value = tableStatePrefix(table) + safeTableKey(key);
        if (value.length() > AddonStateRepository.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state table key is too long");
        }
        return value;
    }

    private static int tableStateKeyLength(String table, String key) {
        return AddonStateRepository.TABLE_STATE_KEY_PREFIX.length() + table.length() + 1 + key.length();
    }

    private static String safeBulkTableName(String table) {
        try {
            return safeTableName(table);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String safeTableName(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith(AddonStateRepository.TABLE_STATE_KEY_PREFIX)) {
            value = value.substring(AddonStateRepository.TABLE_STATE_KEY_PREFIX.length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state table is required");
        }
        if (value.contains("/")) {
            throw new IllegalArgumentException("Addon state table cannot contain /");
        }
        if (value.length() > AddonStateRepository.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state table is too long");
        }
        return value;
    }

    private static String safeTableKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state table key is required");
        }
        return value;
    }

    private static String safeBulkTableKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static String safeBulkRootKey(String key) {
        String value = key == null ? "" : key.trim();
        return value.length() > AddonStateRepository.MAX_KEY_LENGTH
            ? value.substring(0, AddonStateRepository.MAX_KEY_LENGTH)
            : value;
    }

    private static String safeBulkValue(String value) {
        String safe = value == null ? "" : value;
        return safe.length() > AddonStateRepository.MAX_VALUE_LENGTH
            ? safe.substring(0, AddonStateRepository.MAX_VALUE_LENGTH)
            : safe;
    }

    static String addonStateJson(Map<String, String> values) {
        LinkedHashMap<String, Object> renderedValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : (values == null ? Map.<String, String>of() : values).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            renderedValues.put(entry.getKey(), entry.getValue());
        }
        return SimpleJson.stringify(Map.of("values", renderedValues));
    }

    private static void publishAddonStateChanged(GlobalEventPublisher events, Map<String, String> fields) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        if (fields != null) {
            payload.putAll(fields);
        }
        payload.putIfAbsent("cacheTargets", "ADDON_STATE,SUMMARY");
        payload.putIfAbsent("cachePolicy", "invalidate-addon-state-and-summary");
        events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.copyOf(payload));
    }

    static String addonStateSummaryJson(Map<String, Integer> globalCounts, Map<String, Integer> islandCounts) {
        TreeSet<String> addonIds = new TreeSet<>();
        addonIds.addAll(globalCounts == null ? Map.<String, Integer>of().keySet() : globalCounts.keySet());
        addonIds.addAll(islandCounts == null ? Map.<String, Integer>of().keySet() : islandCounts.keySet());
        List<Object> addons = new ArrayList<>();
        for (String addonId : addonIds) {
            int globalKeys = globalCounts == null ? 0 : globalCounts.getOrDefault(addonId, 0);
            int islandKeys = islandCounts == null ? 0 : islandCounts.getOrDefault(addonId, 0);
            LinkedHashMap<String, Object> addon = new LinkedHashMap<>();
            addon.put("addonId", addonId);
            addon.put("globalKeys", globalKeys);
            addon.put("islandKeys", islandKeys);
            addon.put("totalKeys", globalKeys + islandKeys);
            addons.add(addon);
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("stateOwnership", "core-addon-state-store");
        root.put("registeredAddonRequired", false);
        root.put("orphanStatePolicy", "preserve-for-reinstall-or-admin-clear");
        root.put("missingAddonStatePolicy", "ignored-by-island-lifecycle");
        root.put("tableKeyPrefix", AddonStateRepository.TABLE_STATE_KEY_SHAPE);
        root.put("maxAddonIdLength", AddonStateRepository.MAX_ADDON_ID_LENGTH);
        root.put("maxKeyLength", AddonStateRepository.MAX_KEY_LENGTH);
        root.put("maxValueLength", AddonStateRepository.MAX_VALUE_LENGTH);
        root.put("maxKeysPerAddon", AddonStateRepository.MAX_KEYS_PER_ADDON);
        root.put("addons", addons);
        return SimpleJson.stringify(root);
    }
}
