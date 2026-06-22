package kr.lunaf.cloudislands.velocity.message;

import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;

public final class VelocityCoreStatusMessageFormatter {
    public String maintenance(String label, AdminMaintenanceResultView view) {
        if (view == null) {
            return label + ": accepted sessions=0 tickets=0";
        }
        if (!view.code().isBlank()) {
            return label + ": failed code=" + view.code();
        }
        return label + ": accepted sessions=" + view.clearedSessions() + " tickets=" + view.clearedTickets();
    }

    public String metrics(AdminMetricsSummaryView view) {
        if (view == null || view.samples() == 0L) {
            return "Core metrics: empty";
        }
        return "Core metrics: samples=" + view.samples() + (view.names().isEmpty() ? "" : " / " + String.join(", ", view.names()));
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
