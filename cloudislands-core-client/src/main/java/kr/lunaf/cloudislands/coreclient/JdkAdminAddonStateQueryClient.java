package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class JdkAdminAddonStateQueryClient implements AdminAddonStateQueryClient {
    private final JdkCoreApiClient core;

    public JdkAdminAddonStateQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminAddonStateSummaryView> summary() {
        return core.postBody("/v1/admin/addons/state/summary", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminAddonStateQueryClient::summary);
    }

    static AdminAddonStateSummaryView summary(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminAddonStateSummaryView.AddonView> addons = CoreJson.objects(root, "addons").stream()
            .map(addon -> new AdminAddonStateSummaryView.AddonView(
                CoreJson.text(addon, "addonId"),
                CoreJson.number(addon, "globalKeys"),
                CoreJson.number(addon, "islandKeys"),
                CoreJson.number(addon, "totalKeys")
            ))
            .filter(addon -> !addon.addonId().isBlank())
            .toList();
        return new AdminAddonStateSummaryView(
            CoreJson.text(root, "stateOwnership"),
            CoreJson.bool(root, "registeredAddonRequired"),
            CoreJson.text(root, "orphanStatePolicy"),
            CoreJson.text(root, "missingAddonStatePolicy"),
            CoreJson.text(root, "tableKeyPrefix"),
            CoreJson.number(root, "maxKeysPerAddon"),
            CoreJson.number(root, "maxValueLength"),
            addons
        );
    }
}
