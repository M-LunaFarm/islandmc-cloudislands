package kr.lunaf.cloudislands.api.addon;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.IslandActivationRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivationRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.api.event.IslandDeleteRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandFlagChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandLimitChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandRecoveryRequiredEvent;
import kr.lunaf.cloudislands.api.event.IslandRepairedEvent;
import kr.lunaf.cloudislands.api.event.IslandResetEvent;
import kr.lunaf.cloudislands.api.event.IslandRestoredEvent;
import kr.lunaf.cloudislands.api.event.IslandRestoreRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandRuntimeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandUpgradeEvent;
import kr.lunaf.cloudislands.api.event.IslandWorthChangeEvent;
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

    default void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
    }

    default void onAddonReloaded(CloudIslandsAddonSnapshot snapshot) {
        onAddonRegistered(snapshot);
    }

    default void onAddonUnregistered() {
    }

    default void onCloudEvent(CloudEvent event) {
        if (event instanceof IslandCreatedEvent created) {
            onIslandCreated(created);
        } else if (event instanceof IslandActivationRequestEvent activationRequested) {
            onIslandActivationRequested(activationRequested);
        } else if (event instanceof IslandActivatedEvent activated) {
            onIslandActivated(activated);
        } else if (event instanceof IslandDeactivationRequestEvent deactivationRequested) {
            onIslandDeactivationRequested(deactivationRequested);
        } else if (event instanceof IslandDeactivateEvent deactivated) {
            onIslandDeactivated(deactivated);
        } else if (event instanceof IslandMigratedEvent migrated) {
            onIslandMigrated(migrated);
        } else if (event instanceof IslandDeletedEvent deleted) {
            onIslandDeleted(deleted);
        } else if (event instanceof IslandDeleteRequestEvent deleteRequested) {
            onIslandDeleteRequested(deleteRequested);
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
        } else if (event instanceof IslandMemberChangedEvent memberChanged) {
            onIslandMemberChanged(memberChanged);
        } else if (event instanceof IslandFlagChangeEvent flagChanged) {
            onIslandFlagChanged(flagChanged);
        } else if (event instanceof IslandPermissionChangeEvent permissionChanged) {
            onIslandPermissionChanged(permissionChanged);
        } else if (event instanceof IslandLevelRecalculateEvent levelUpdated) {
            onIslandLevelUpdated(levelUpdated);
            if (levelUpdated.worth() != null) {
                onIslandWorthChanged(new IslandWorthChangeEvent(levelUpdated.islandId(), levelUpdated.worth(), levelUpdated.occurredAt()));
            }
        } else if (event instanceof IslandWorthChangeEvent worthChanged) {
            onIslandWorthChanged(worthChanged);
        } else if (event instanceof IslandUpgradeEvent upgrade) {
            onIslandUpgradeChanged(upgrade);
        } else if (event instanceof IslandLimitChangeEvent limit) {
            onIslandLimitChanged(limit);
        } else if (event instanceof IslandSnapshotRequestEvent snapshotRequested) {
            onIslandSnapshotRequested(snapshotRequested);
        } else if (event instanceof IslandSnapshotCreateEvent snapshotCreated) {
            onIslandSnapshotCreated(snapshotCreated);
        }
    }

    default void onIslandCreated(IslandCreatedEvent event) {
    }

    default void onIslandActivationRequested(IslandActivationRequestEvent event) {
    }

    default void onIslandActivated(IslandActivatedEvent event) {
    }

    default void onIslandDeactivationRequested(IslandDeactivationRequestEvent event) {
    }

    default void onIslandDeactivated(IslandDeactivateEvent event) {
    }

    default void onIslandMigrated(IslandMigratedEvent event) {
    }

    default void onIslandDeleted(IslandDeletedEvent event) {
    }

    default void onIslandDeleteRequested(IslandDeleteRequestEvent event) {
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

    default void onIslandMemberChanged(IslandMemberChangedEvent event) {
    }

    default void onIslandFlagChanged(IslandFlagChangeEvent event) {
    }

    default void onIslandPermissionChanged(IslandPermissionChangeEvent event) {
    }

    default void onIslandLevelUpdated(IslandLevelRecalculateEvent event) {
    }

    default void onIslandWorthChanged(IslandWorthChangeEvent event) {
    }

    default void onIslandUpgradeChanged(IslandUpgradeEvent event) {
    }

    default void onIslandLimitChanged(IslandLimitChangeEvent event) {
    }

    default void onIslandSnapshotRequested(IslandSnapshotRequestEvent event) {
    }

    default void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
    }

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsApi api) {
        return api.addons().register(this);
    }

    default CompletableFuture<Void> unregister(CloudIslandsApi api) {
        return api.addons().unregister(safeAddonId());
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
