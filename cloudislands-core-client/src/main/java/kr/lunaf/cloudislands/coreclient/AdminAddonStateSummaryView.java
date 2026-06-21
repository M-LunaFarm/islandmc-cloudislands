package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record AdminAddonStateSummaryView(
    String stateOwnership,
    boolean registeredAddonRequired,
    String orphanStatePolicy,
    String missingAddonStatePolicy,
    String tableKeyPrefix,
    long maxKeysPerAddon,
    long maxValueLength,
    List<AddonView> addons
) {
    public AdminAddonStateSummaryView {
        stateOwnership = stateOwnership == null ? "" : stateOwnership;
        orphanStatePolicy = orphanStatePolicy == null ? "" : orphanStatePolicy;
        missingAddonStatePolicy = missingAddonStatePolicy == null ? "" : missingAddonStatePolicy;
        tableKeyPrefix = tableKeyPrefix == null ? "" : tableKeyPrefix;
        addons = addons == null ? List.of() : List.copyOf(addons);
    }

    public record AddonView(String addonId, long globalKeys, long islandKeys, long totalKeys) {
        public AddonView {
            addonId = addonId == null ? "" : addonId;
        }
    }
}
