package kr.lunaf.cloudislands.api.addon;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
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
        } else if (event instanceof IslandActivatedEvent activated) {
            onIslandActivated(activated);
        } else if (event instanceof IslandDeactivateEvent deactivated) {
            onIslandDeactivated(deactivated);
        } else if (event instanceof IslandMigratedEvent migrated) {
            onIslandMigrated(migrated);
        } else if (event instanceof IslandDeletedEvent deleted) {
            onIslandDeleted(deleted);
        } else if (event instanceof IslandMemberChangedEvent memberChanged) {
            onIslandMemberChanged(memberChanged);
        } else if (event instanceof IslandPermissionChangeEvent permissionChanged) {
            onIslandPermissionChanged(permissionChanged);
        } else if (event instanceof IslandLevelRecalculateEvent levelUpdated) {
            onIslandLevelUpdated(levelUpdated);
            if (levelUpdated.worth() != null) {
                onIslandWorthChanged(new IslandWorthChangeEvent(levelUpdated.islandId(), levelUpdated.worth(), levelUpdated.occurredAt()));
            }
        } else if (event instanceof IslandWorthChangeEvent worthChanged) {
            onIslandWorthChanged(worthChanged);
        } else if (event instanceof IslandSnapshotCreateEvent snapshotCreated) {
            onIslandSnapshotCreated(snapshotCreated);
        }
    }

    default void onIslandCreated(IslandCreatedEvent event) {
    }

    default void onIslandActivated(IslandActivatedEvent event) {
    }

    default void onIslandDeactivated(IslandDeactivateEvent event) {
    }

    default void onIslandMigrated(IslandMigratedEvent event) {
    }

    default void onIslandDeleted(IslandDeletedEvent event) {
    }

    default void onIslandMemberChanged(IslandMemberChangedEvent event) {
    }

    default void onIslandPermissionChanged(IslandPermissionChangeEvent event) {
    }

    default void onIslandLevelUpdated(IslandLevelRecalculateEvent event) {
    }

    default void onIslandWorthChanged(IslandWorthChangeEvent event) {
    }

    default void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
    }

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsApi api) {
        return api.addons().register(this);
    }

    default CompletableFuture<Void> unregister(CloudIslandsApi api) {
        return api.addons().unregister(addonId());
    }
}
