package kr.lunaf.cloudislands.velocity.message;

import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;

public final class VelocityCoreConfigMessageFormatter {
    public String format(AdminCoreConfigView view) {
        if (view == null) {
            return "Core config: unavailable";
        }
        if (!view.code().isBlank()) {
            return "Core config: failed code=" + view.code();
        }
        return "Core config: repo=" + view.text("repositoryMode")
            + " jobs=" + view.text("jobQueueMode")
            + " events=" + view.text("eventBusMode")
            + " effectiveRepo=" + view.text("effectiveRepositoryMode")
            + " effectiveJobs=" + view.text("effectiveJobQueueMode")
            + " storage=" + view.text("storageType")
            + " islandModel=" + view.text("islandResourceModel")
            + " portableBundle=" + view.bool("islandPortableBundle")
            + " serverPinned=" + view.bool("islandServerPinned")
            + " islandExecution=" + view.text("islandExecutionModel")
            + " islandRouting=" + view.text("islandRoutingModel")
            + " createFlow=" + view.text("createIslandRequestFlow")
            + " homeFlow=" + view.text("homeRequestFlow")
            + " visitFlow=" + view.text("visitRequestFlow")
            + " routeUi=" + view.text("routePlayerLoadingUi")
            + " modules=" + view.text("moduleLayout")
            + " dist=" + view.text("distributionLayout")
            + " addonRegistry=" + view.text("addonRegistryPolicy")
            + " addonStateOwner=" + view.text("addonStateOwnershipPolicy")
            + " addonApiContract=" + view.text("addonApiContractVersion")
            + " addonApiContractCompatible=" + view.text("addonApiContractCompatible")
            + " authPolicy=" + view.text("coreApiAuthPolicy")
            + " adminPolicy=" + view.text("adminPermissionPolicy")
            + " auditPolicy=" + view.text("auditLogPolicy")
            + " dbBackend=" + view.text("databaseBackend")
            + " jdbcSource=" + view.text("jdbcUrlSource")
            + " jdbcSupported=" + view.bool("coreJdbcSupported")
            + " addonBulkSave=" + view.bool("addonStateBulkSaveApi")
            + " addonTablePrefix=" + view.text("addonStateTableKeyPrefix")
            + " addonMaxKeys=" + view.number("addonStateMaxKeysPerAddon")
            + " addonMaxValue=" + view.number("addonStateMaxValueLength")
            + " pool=" + view.text("islandPool")
            + " poolNodes=" + view.number("islandPoolNodeCount")
            + " poolRouteCandidates=" + view.number("islandPoolRouteCandidateCount")
            + " poolScale=" + view.text("islandPoolScaleStatus")
            + " placement=" + view.text("islandPlacementPolicy")
            + " dbPool=" + view.number("databasePoolSize")
            + " softFull=" + view.text("softFullPolicy")
            + " hardFull=" + view.text("hardFullPolicy")
            + " migration=" + view.text("migrationPolicy")
            + " ticketTtl=" + view.number("routeTicketTtlSeconds") + "s"
            + " heartbeatTimeout=" + view.number("heartbeatTimeoutSeconds") + "s"
            + " leaseDuration=" + view.number("leaseDurationSeconds") + "s"
            + " redisTtl=" + view.text("redisCacheTtlPolicy")
            + " redisKeys=" + view.text("redisKeyPolicy")
            + " lockPolicy=" + view.text("distributedLockPolicy")
            + " fencing=" + view.text("fencingTokenPolicy")
            + " staleWrite=" + view.text("staleWritePolicy")
            + " storageLayout=" + view.text("storageLayout")
            + " snapshotLatest=" + view.number("snapshotKeepLatest")
            + " routeDuplicatePolicy=" + view.text("routeDuplicateTicketPolicy");
    }

}
