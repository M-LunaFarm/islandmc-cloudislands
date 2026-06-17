package kr.lunaf.cloudislands.api.addon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.event.AddonStateChangeEvent;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.CoreCacheClearEvent;
import kr.lunaf.cloudislands.api.event.CoreReloadEvent;
import kr.lunaf.cloudislands.api.event.IslandActivationRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandAccessChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberJoinEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberLeaveEvent;
import kr.lunaf.cloudislands.api.event.IslandBankChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandBiomeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandBlockValueChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandBlocksChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandChatSentEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandPreActivateEvent;
import kr.lunaf.cloudislands.api.event.IslandPreCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandPreVisitEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivationRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeleteBackupFailedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeleteRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandFlagChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandHomeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandInviteChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandLimitChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigrationEvent;
import kr.lunaf.cloudislands.api.event.IslandMissionCompleteEvent;
import kr.lunaf.cloudislands.api.event.IslandMissionProgressEvent;
import kr.lunaf.cloudislands.api.event.IslandOwnershipChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionCheckEvent;
import kr.lunaf.cloudislands.api.event.IslandRecoveryRequiredEvent;
import kr.lunaf.cloudislands.api.event.IslandRenamedEvent;
import kr.lunaf.cloudislands.api.event.IslandRepairedEvent;
import kr.lunaf.cloudislands.api.event.IslandResetEvent;
import kr.lunaf.cloudislands.api.event.IslandRestoredEvent;
import kr.lunaf.cloudislands.api.event.IslandRestoreRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandRoleCatalogChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandRoleChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandRuntimeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandTemplateChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandUpgradeEvent;
import kr.lunaf.cloudislands.api.event.IslandVisitorBanChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandVisitorKickEvent;
import kr.lunaf.cloudislands.api.event.IslandVisitEvent;
import kr.lunaf.cloudislands.api.event.IslandWarpChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandWarpCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandWarpDeleteEvent;
import kr.lunaf.cloudislands.api.event.IslandWorthChangeEvent;
import kr.lunaf.cloudislands.api.event.NodeStateChangedEvent;
import kr.lunaf.cloudislands.api.event.RouteSessionPublishedEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketClearedEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketConsumedGlobalEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketCreatedEvent;
import kr.lunaf.cloudislands.api.event.RouteTicketFailedEvent;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public interface CloudIslandsAddon {
    String addonId();

    String addonDisplayName();

    String addonVersion();

    default boolean enabledByDefault() {
        return true;
    }

    default Map<String, Boolean> addonFeatures() {
        return Map.of();
    }

    default Map<String, String> addonMetadata() {
        return Map.of();
    }

    default String addonPackaging() {
        return "external-plugin";
    }

    default boolean addonOwnsIslands() {
        return false;
    }

    default boolean addonRemovalSafe() {
        return true;
    }

    default String addonDataRetentionPolicy() {
        return "preserve-addon-state-by-addon-id-and-island-uuid";
    }

    default String addonDescriptorResource() {
        return "";
    }

    default List<String> addonLifecycleEvents() {
        return List.of(
            "island-pre-create",
            "island-created",
            "island-pre-activate",
            "island-activation-requested",
            "island-activated",
            "island-deactivation-requested",
            "island-deactivated",
            "island-migration-requested",
            "island-migrated",
            "island-delete-requested",
            "island-deleted",
            "island-delete-backup-failed",
            "island-restore-requested",
            "island-restored",
            "island-reset",
            "island-recovery-required",
            "island-repaired",
            "island-runtime-changed",
            "island-pre-visit",
            "island-visited",
            "island-invite-changed",
            "island-member-joined",
            "island-member-left",
            "island-member-changed",
            "island-renamed",
            "island-access-changed",
            "island-visitor-ban-changed",
            "island-visitor-kicked",
            "island-flag-changed",
            "island-permission-checked",
            "island-permission-changed",
            "island-role-changed",
            "island-role-catalog-changed",
            "island-ownership-changed",
            "island-chat-sent",
            "island-blocks-changed",
            "island-block-value-changed",
            "island-mission-progress",
            "island-mission-completed",
            "island-level-recalculate",
            "island-worth-changed",
            "island-upgrade-changed",
            "island-limit-changed",
            "island-biome-changed",
            "island-home-changed",
            "island-warp-created",
            "island-warp-deleted",
            "island-warp-changed",
            "island-bank-changed",
            "island-snapshot-requested",
            "island-snapshot-created",
            "island-template-changed",
            "node-state-changed",
            "route-ticket-created",
            "route-session-published",
            "route-ticket-consumed",
            "route-ticket-failed",
            "route-ticket-cleared",
            "addon-state-changed",
            "core-cache-cleared",
            "core-reloaded"
        );
    }

    default Map<String, String> addonStandardMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("addon-packaging", safeMetadataValue(addonPackaging(), "external-plugin"));
        metadata.put("addon-runtime-owns-islands", Boolean.toString(addonOwnsIslands()));
        metadata.put("addon-removal-safe", Boolean.toString(addonRemovalSafe()));
        metadata.put("addon-data-retention", safeMetadataValue(addonDataRetentionPolicy(), "preserve-addon-state-by-addon-id-and-island-uuid"));
        metadata.put("addon-event-source", "cloudislands-global-event-stream");
        metadata.put("addon-event-delivery", "typed-cloud-event-callbacks-through-cloudislands-api");
        metadata.put("addon-event-failure-policy", "addon-callback-exceptions-are-logged-and-isolated");
        metadata.put("addon-event-feature-gating-policy", "disabled-addon-features-do-not-receive-matching-runtime-events");
        metadata.put("addon-lifecycle-events", String.join(",", addonLifecycleEvents()));
        String descriptor = addonDescriptorResource();
        if (descriptor != null && !descriptor.isBlank()) {
            metadata.put("addon-descriptor-resource", descriptor);
        }
        return Map.copyOf(metadata);
    }

    private static String safeMetadataValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    default void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
    }

    default void onAddonReloaded(CloudIslandsAddonSnapshot snapshot) {
        onAddonRegistered(snapshot);
    }

    default void onAddonUnregistered() {
    }

    default void onCloudEvent(CloudEvent event) {
        if (event instanceof IslandPreCreateEvent preCreate) {
            onIslandPreCreate(preCreate);
        } else if (event instanceof IslandCreatedEvent created) {
            onIslandCreated(created);
        } else if (event instanceof IslandPreActivateEvent preActivate) {
            onIslandPreActivate(preActivate);
        } else if (event instanceof IslandActivationRequestEvent activationRequested) {
            onIslandActivationRequested(activationRequested);
        } else if (event instanceof IslandActivatedEvent activated) {
            onIslandActivated(activated);
        } else if (event instanceof IslandDeactivationRequestEvent deactivationRequested) {
            onIslandDeactivationRequested(deactivationRequested);
        } else if (event instanceof IslandDeactivatedEvent deactivated) {
            onIslandDeactivated(deactivated);
        } else if (event instanceof IslandMigrationEvent migrationRequested) {
            onIslandMigrationRequested(migrationRequested);
        } else if (event instanceof IslandMigratedEvent migrated) {
            onIslandMigrated(migrated);
        } else if (event instanceof IslandDeletedEvent deleted) {
            onIslandDeleted(deleted);
        } else if (event instanceof IslandDeleteRequestEvent deleteRequested) {
            onIslandDeleteRequested(deleteRequested);
        } else if (event instanceof IslandDeleteBackupFailedEvent deleteBackupFailed) {
            onIslandDeleteBackupFailed(deleteBackupFailed);
        } else if (event instanceof IslandRestoreRequestEvent restoreRequested) {
            onIslandRestoreRequested(restoreRequested);
        } else if (event instanceof IslandRestoredEvent restored) {
            onIslandRestored(restored);
        } else if (event instanceof IslandResetEvent reset) {
            onIslandReset(reset);
        } else if (event instanceof IslandRecoveryRequiredEvent recoveryRequired) {
            onIslandRecoveryRequired(recoveryRequired);
        } else if (event instanceof IslandRepairedEvent repaired) {
            onIslandRepaired(repaired);
        } else if (event instanceof IslandRuntimeChangeEvent runtimeChanged) {
            onIslandRuntimeChanged(runtimeChanged);
        } else if (event instanceof IslandPreVisitEvent preVisit) {
            onIslandPreVisit(preVisit);
        } else if (event instanceof IslandVisitEvent visited) {
            onIslandVisited(visited);
        } else if (event instanceof IslandInviteChangeEvent inviteChanged) {
            onIslandInviteChanged(inviteChanged);
        } else if (event instanceof IslandMemberJoinEvent memberJoined) {
            onIslandMemberJoined(memberJoined);
        } else if (event instanceof IslandMemberLeaveEvent memberLeft) {
            onIslandMemberLeft(memberLeft);
        } else if (event instanceof IslandMemberChangedEvent memberChanged) {
            onIslandMemberChanged(memberChanged);
        } else if (event instanceof IslandRenamedEvent renamed) {
            onIslandRenamed(renamed);
        } else if (event instanceof IslandAccessChangeEvent accessChanged) {
            onIslandAccessChanged(accessChanged);
        } else if (event instanceof IslandVisitorBanChangeEvent visitorBanChanged) {
            onIslandVisitorBanChanged(visitorBanChanged);
        } else if (event instanceof IslandVisitorKickEvent visitorKicked) {
            onIslandVisitorKicked(visitorKicked);
        } else if (event instanceof IslandFlagChangeEvent flagChanged) {
            onIslandFlagChanged(flagChanged);
        } else if (event instanceof IslandPermissionChangeEvent permissionChanged) {
            onIslandPermissionChanged(permissionChanged);
        } else if (event instanceof IslandPermissionCheckEvent permissionChecked) {
            onIslandPermissionChecked(permissionChecked);
        } else if (event instanceof IslandRoleChangeEvent roleChanged) {
            onIslandRoleChanged(roleChanged);
        } else if (event instanceof IslandRoleCatalogChangeEvent roleCatalogChanged) {
            onIslandRoleCatalogChanged(roleCatalogChanged);
        } else if (event instanceof IslandOwnershipChangeEvent ownershipChanged) {
            onIslandOwnershipChanged(ownershipChanged);
        } else if (event instanceof IslandChatSentEvent chatSent) {
            onIslandChatSent(chatSent);
        } else if (event instanceof IslandBlocksChangeEvent blocksChanged) {
            onIslandBlocksChanged(blocksChanged);
        } else if (event instanceof IslandBlockValueChangeEvent blockValueChanged) {
            onIslandBlockValueChanged(blockValueChanged);
        } else if (event instanceof IslandMissionProgressEvent missionProgress) {
            onIslandMissionProgress(missionProgress);
        } else if (event instanceof IslandMissionCompleteEvent missionCompleted) {
            onIslandMissionCompleted(missionCompleted);
        } else if (event instanceof IslandLevelRecalculateEvent levelUpdated) {
            onIslandLevelUpdated(levelUpdated);
        } else if (event instanceof IslandWorthChangeEvent worthChanged) {
            onIslandWorthChanged(worthChanged);
        } else if (event instanceof IslandUpgradeEvent upgrade) {
            onIslandUpgradeChanged(upgrade);
        } else if (event instanceof IslandLimitChangeEvent limit) {
            onIslandLimitChanged(limit);
        } else if (event instanceof IslandBiomeChangeEvent biomeChanged) {
            onIslandBiomeChanged(biomeChanged);
        } else if (event instanceof IslandHomeChangeEvent homeChanged) {
            onIslandHomeChanged(homeChanged);
        } else if (event instanceof IslandWarpCreateEvent warpCreated) {
            onIslandWarpCreated(warpCreated);
        } else if (event instanceof IslandWarpDeleteEvent warpDeleted) {
            onIslandWarpDeleted(warpDeleted);
        } else if (event instanceof IslandWarpChangeEvent warpChanged) {
            onIslandWarpChanged(warpChanged);
        } else if (event instanceof IslandBankChangeEvent bankChanged) {
            onIslandBankChanged(bankChanged);
        } else if (event instanceof IslandSnapshotRequestEvent snapshotRequested) {
            onIslandSnapshotRequested(snapshotRequested);
        } else if (event instanceof IslandSnapshotCreateEvent snapshotCreated) {
            onIslandSnapshotCreated(snapshotCreated);
        } else if (event instanceof NodeStateChangedEvent nodeStateChanged) {
            onNodeStateChanged(nodeStateChanged);
        } else if (event instanceof RouteTicketCreatedEvent routeTicketCreated) {
            onRouteTicketCreated(routeTicketCreated);
        } else if (event instanceof RouteSessionPublishedEvent routeSessionPublished) {
            onRouteSessionPublished(routeSessionPublished);
        } else if (event instanceof RouteTicketConsumedGlobalEvent routeTicketConsumed) {
            onRouteTicketConsumed(routeTicketConsumed);
        } else if (event instanceof RouteTicketFailedEvent routeTicketFailed) {
            onRouteTicketFailed(routeTicketFailed);
        } else if (event instanceof RouteTicketClearedEvent routeTicketCleared) {
            onRouteTicketCleared(routeTicketCleared);
        } else if (event instanceof IslandTemplateChangeEvent templateChanged) {
            onIslandTemplateChanged(templateChanged);
        } else if (event instanceof AddonStateChangeEvent addonStateChanged) {
            onAddonStateChanged(addonStateChanged);
        } else if (event instanceof CoreCacheClearEvent cacheCleared) {
            onCoreCacheCleared(cacheCleared);
        } else if (event instanceof CoreReloadEvent coreReloaded) {
            onCoreReloaded(coreReloaded);
        }
    }

    default void onIslandCreated(IslandCreatedEvent event) {
    }

    default void onIslandPreCreate(IslandPreCreateEvent event) {
    }

    default void onIslandPreActivate(IslandPreActivateEvent event) {
    }

    default void onIslandActivationRequested(IslandActivationRequestEvent event) {
    }

    default void onIslandActivated(IslandActivatedEvent event) {
    }

    default void onIslandDeactivationRequested(IslandDeactivationRequestEvent event) {
    }

    default void onIslandDeactivated(IslandDeactivatedEvent event) {
    }

    default void onIslandMigrationRequested(IslandMigrationEvent event) {
    }

    default void onIslandMigrated(IslandMigratedEvent event) {
    }

    default void onIslandDeleted(IslandDeletedEvent event) {
    }

    default void onIslandDeleteRequested(IslandDeleteRequestEvent event) {
    }

    default void onIslandDeleteBackupFailed(IslandDeleteBackupFailedEvent event) {
    }

    default void onIslandRestoreRequested(IslandRestoreRequestEvent event) {
    }

    default void onIslandRestored(IslandRestoredEvent event) {
    }

    default void onIslandReset(IslandResetEvent event) {
    }

    default void onIslandRecoveryRequired(IslandRecoveryRequiredEvent event) {
    }

    default void onIslandRepaired(IslandRepairedEvent event) {
    }

    default void onIslandRuntimeChanged(IslandRuntimeChangeEvent event) {
    }

    default void onIslandPreVisit(IslandPreVisitEvent event) {
    }

    default void onIslandInviteChanged(IslandInviteChangeEvent event) {
    }

    default void onIslandMemberJoined(IslandMemberJoinEvent event) {
    }

    default void onIslandMemberLeft(IslandMemberLeaveEvent event) {
    }

    default void onIslandMemberChanged(IslandMemberChangedEvent event) {
    }

    default void onIslandRenamed(IslandRenamedEvent event) {
    }

    default void onIslandAccessChanged(IslandAccessChangeEvent event) {
    }

    default void onIslandVisitorBanChanged(IslandVisitorBanChangeEvent event) {
    }

    default void onIslandVisitorKicked(IslandVisitorKickEvent event) {
    }

    default void onIslandFlagChanged(IslandFlagChangeEvent event) {
    }

    default void onIslandPermissionChanged(IslandPermissionChangeEvent event) {
    }

    default void onIslandPermissionChecked(IslandPermissionCheckEvent event) {
    }

    default void onIslandRoleChanged(IslandRoleChangeEvent event) {
    }

    default void onIslandRoleCatalogChanged(IslandRoleCatalogChangeEvent event) {
    }

    default void onIslandOwnershipChanged(IslandOwnershipChangeEvent event) {
    }

    default void onIslandChatSent(IslandChatSentEvent event) {
    }

    default void onIslandBlocksChanged(IslandBlocksChangeEvent event) {
    }

    default void onIslandBlockValueChanged(IslandBlockValueChangeEvent event) {
    }

    default void onIslandMissionProgress(IslandMissionProgressEvent event) {
    }

    default void onIslandMissionCompleted(IslandMissionCompleteEvent event) {
    }

    default void onIslandLevelUpdated(IslandLevelRecalculateEvent event) {
    }

    default void onIslandWorthChanged(IslandWorthChangeEvent event) {
    }

    default void onIslandUpgradeChanged(IslandUpgradeEvent event) {
    }

    default void onIslandLimitChanged(IslandLimitChangeEvent event) {
    }

    default void onIslandBiomeChanged(IslandBiomeChangeEvent event) {
    }

    default void onIslandHomeChanged(IslandHomeChangeEvent event) {
    }

    default void onIslandWarpChanged(IslandWarpChangeEvent event) {
    }

    default void onIslandWarpCreated(IslandWarpCreateEvent event) {
    }

    default void onIslandWarpDeleted(IslandWarpDeleteEvent event) {
    }

    default void onIslandBankChanged(IslandBankChangeEvent event) {
    }

    default void onIslandSnapshotRequested(IslandSnapshotRequestEvent event) {
    }

    default void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
    }

    default void onNodeStateChanged(NodeStateChangedEvent event) {
    }

    default void onIslandVisited(IslandVisitEvent event) {
    }

    default void onRouteTicketCreated(RouteTicketCreatedEvent event) {
    }

    default void onRouteSessionPublished(RouteSessionPublishedEvent event) {
    }

    default void onRouteTicketConsumed(RouteTicketConsumedGlobalEvent event) {
    }

    default void onRouteTicketFailed(RouteTicketFailedEvent event) {
    }

    default void onRouteTicketCleared(RouteTicketClearedEvent event) {
    }

    default void onIslandTemplateChanged(IslandTemplateChangeEvent event) {
    }

    default void onAddonStateChanged(AddonStateChangeEvent event) {
    }

    default void onCoreCacheCleared(CoreCacheClearEvent event) {
    }

    default void onCoreReloaded(CoreReloadEvent event) {
    }

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsApi api) {
        return api.addons().register(this);
    }

    default CompletableFuture<Void> unregister(CloudIslandsApi api) {
        return api.addons().unregisterPreservingState(safeAddonId());
    }

    private String safeAddonId() {
        try {
            String id = addonId();
            return id == null || id.isBlank() ? getClass().getName() : id;
        } catch (RuntimeException ignored) {
            return getClass().getName();
        }
    }
}
