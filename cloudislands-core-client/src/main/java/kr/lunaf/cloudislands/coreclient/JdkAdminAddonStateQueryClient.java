package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

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
        return core.post("/v1/admin/addons/state/summary", "{}").thenApply(JdkAdminAddonStateQueryClient::summary);
    }

    static AdminAddonStateSummaryView summary(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminAddonStateSummaryView.AddonView> addons = SimpleJson.list(root.get("addons")).stream()
            .map(SimpleJson::object)
            .map(addon -> new AdminAddonStateSummaryView.AddonView(
                text(addon, "addonId"),
                number(addon, "globalKeys"),
                number(addon, "islandKeys"),
                number(addon, "totalKeys")
            ))
            .filter(addon -> !addon.addonId().isBlank())
            .toList();
        return new AdminAddonStateSummaryView(
            text(root, "stateOwnership"),
            bool(root, "registeredAddonRequired"),
            text(root, "orphanStatePolicy"),
            text(root, "missingAddonStatePolicy"),
            text(root, "tableKeyPrefix"),
            number(root, "maxKeysPerAddon"),
            number(root, "maxValueLength"),
            addons
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(SimpleJson.text(value));
    }
}
