package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;

import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;

public final class VelocityCoreStatusMessageFormatter {
    public String maintenance(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": failed code=" + code;
        }
        return label + ": accepted sessions=" + longValue(body, "clearedSessions") + " tickets=" + longValue(body, "clearedTickets");
    }

    public String maintenance(String label, AdminMaintenanceResultView view) {
        if (view == null) {
            return label + ": accepted sessions=0 tickets=0";
        }
        if (!view.code().isBlank()) {
            return label + ": failed code=" + view.code();
        }
        return label + ": accepted sessions=" + view.clearedSessions() + " tickets=" + view.clearedTickets();
    }

    public String metrics(String body) {
        if (body == null || body.isBlank()) {
            return "Core metrics: empty";
        }
        int samples = 0;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            samples++;
            if (names.size() < 6) {
                int brace = trimmed.indexOf('{');
                int space = trimmed.indexOf(' ');
                int end = brace > 0 ? brace : space > 0 ? space : trimmed.length();
                String name = trimmed.substring(0, end);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return "Core metrics: samples=" + samples + (names.isEmpty() ? "" : " / " + String.join(", ", names));
    }

    public String metrics(AdminMetricsSummaryView view) {
        if (view == null || view.samples() == 0L) {
            return "Core metrics: empty";
        }
        return "Core metrics: samples=" + view.samples() + (view.names().isEmpty() ? "" : " / " + String.join(", ", view.names()));
    }

    public String addonEndpoints(String body) {
        return "Addon endpoints: "
            + "bulkSave=" + boolValue(body, "addonStateBulkSaveApi")
            + " global=" + jsonValue(body, "addonStateBulkSaveGlobalEndpoint")
            + " island=" + jsonValue(body, "addonStateBulkSaveIslandEndpoint")
            + " tableGlobal=" + jsonValue(body, "addonStateTableKeyValueBulkSaveGlobalEndpoint")
            + " tableIsland=" + jsonValue(body, "addonStateTableKeyValueBulkSaveIslandEndpoint")
            + " tableGlobalAlias=" + jsonValue(body, "addonStateTableKeyValueBulkSaveGlobalAlias")
            + " tableIslandAlias=" + jsonValue(body, "addonStateTableKeyValueBulkSaveIslandAlias")
            + " tableBulkGlobal=" + jsonValue(body, "addonStateTableKeyValueBulkGlobalEndpoint")
            + " tableBulkIsland=" + jsonValue(body, "addonStateTableKeyValueBulkIslandEndpoint")
            + " tableLoadGlobal=" + jsonValue(body, "addonStateTableKeyValueBulkLoadGlobalEndpoint")
            + " tableLoadIsland=" + jsonValue(body, "addonStateTableKeyValueBulkLoadIslandEndpoint")
            + " tableMapGlobal=" + jsonValue(body, "addonStateTableBulkGlobalEndpoint")
            + " tableMapIsland=" + jsonValue(body, "addonStateTableBulkIslandEndpoint")
            + " payload=" + jsonValue(body, "addonStateTableKeyValueBulkSavePayload")
            + " loadPayload=" + jsonValue(body, "addonStateTableKeyValueBulkLoadPayload")
            + " api=" + jsonValue(body, "addonStateTableKeyValueBulkSaveRepositoryApi")
            + " storage=" + jsonValue(body, "addonStateTableKeyValueBulkSaveStorageMode")
            + " tablePrefix=" + jsonValue(body, "addonStateTableKeyPrefix")
            + " maxKeys=" + longValue(body, "addonStateMaxKeysPerAddon")
            + " maxValue=" + longValue(body, "addonStateMaxValueLength")
            + " globalCacheKey=" + jsonValue(body, "addonStateGlobalCacheKey")
            + " islandCacheKey=" + jsonValue(body, "addonStateIslandCacheKey")
            + " invalidationApi=" + jsonValue(body, "addonStateCacheInvalidationApi")
            + " cacheEventFields=" + jsonValue(body, "cacheInvalidationEventFields")
            + " eventTypeKeys=" + jsonValue(body, "globalEventTypeKeys")
            + " eventRecoveryKeys=" + jsonValue(body, "globalEventRecoveryKeys")
            + " eventAddonKeys=" + jsonValue(body, "globalEventAddonKeys")
            + " fallback=" + jsonValue(body, "addonStateTableKeyValueBulkSaveFallback")
            + " loadFallback=" + jsonValue(body, "addonStateTableKeyValueBulkLoadFallback");
    }

    public String addonEndpoints(AdminCoreConfigView view) {
        if (view == null) {
            return "Addon endpoints: unavailable";
        }
        return "Addon endpoints: "
            + "bulkSave=" + view.bool("addonStateBulkSaveApi")
            + " global=" + view.text("addonStateBulkSaveGlobalEndpoint")
            + " island=" + view.text("addonStateBulkSaveIslandEndpoint")
            + " tableGlobal=" + view.text("addonStateTableKeyValueBulkSaveGlobalEndpoint")
            + " tableIsland=" + view.text("addonStateTableKeyValueBulkSaveIslandEndpoint")
            + " tableGlobalAlias=" + view.text("addonStateTableKeyValueBulkSaveGlobalAlias")
            + " tableIslandAlias=" + view.text("addonStateTableKeyValueBulkSaveIslandAlias")
            + " tableBulkGlobal=" + view.text("addonStateTableKeyValueBulkGlobalEndpoint")
            + " tableBulkIsland=" + view.text("addonStateTableKeyValueBulkIslandEndpoint")
            + " tableLoadGlobal=" + view.text("addonStateTableKeyValueBulkLoadGlobalEndpoint")
            + " tableLoadIsland=" + view.text("addonStateTableKeyValueBulkLoadIslandEndpoint")
            + " tableMapGlobal=" + view.text("addonStateTableBulkGlobalEndpoint")
            + " tableMapIsland=" + view.text("addonStateTableBulkIslandEndpoint")
            + " payload=" + view.text("addonStateTableKeyValueBulkSavePayload")
            + " loadPayload=" + view.text("addonStateTableKeyValueBulkLoadPayload")
            + " api=" + view.text("addonStateTableKeyValueBulkSaveRepositoryApi")
            + " storage=" + view.text("addonStateTableKeyValueBulkSaveStorageMode")
            + " tablePrefix=" + view.text("addonStateTableKeyPrefix")
            + " maxKeys=" + view.number("addonStateMaxKeysPerAddon")
            + " maxValue=" + view.number("addonStateMaxValueLength")
            + " globalCacheKey=" + view.text("addonStateGlobalCacheKey")
            + " islandCacheKey=" + view.text("addonStateIslandCacheKey")
            + " invalidationApi=" + view.text("addonStateCacheInvalidationApi")
            + " cacheEventFields=" + view.text("cacheInvalidationEventFields")
            + " eventTypeKeys=" + view.text("globalEventTypeKeys")
            + " eventRecoveryKeys=" + view.text("globalEventRecoveryKeys")
            + " eventAddonKeys=" + view.text("globalEventAddonKeys")
            + " fallback=" + view.text("addonStateTableKeyValueBulkSaveFallback")
            + " loadFallback=" + view.text("addonStateTableKeyValueBulkLoadFallback");
    }
}
