package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreIslandEnvironmentCommandClient implements IslandEnvironmentCommandClient {
    private final CoreApiClient delegate;

    public CoreIslandEnvironmentCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setBiome(UUID islandId, UUID actorUuid, String biomeKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandBiomeResult(islandId, actorUuid, biomeKey == null ? "" : biomeKey)
            .thenApply(body -> actionResult(body, "BIOME_SET"));
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireFlag(flag);
        return delegate.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value)
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    @Override
    public CompletableFuture<EnvironmentActionView> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandLimit(islandId, actorUuid, limitKey == null ? "" : limitKey, value)
            .thenApply(body -> actionResult(body, "LIMIT_SET"));
    }

    private static EnvironmentActionView actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted", true);
        accepted = accepted && !root.containsKey("error") && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new EnvironmentActionView(
            accepted,
            code,
            firstText(root, "limitKey", "biomeKey", "flag", "key"),
            SimpleJson.number(root.get("value"))
        );
    }

    private static String firstText(Map<?, ?> root, String... keys) {
        for (String key : keys) {
            String value = text(root, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Map<?, ?> root, String key) {
        return SimpleJson.text(root.get(key));
    }

    private static boolean bool(Map<?, ?> root, String key, boolean fallback) {
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireFlag(IslandFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("flag is required");
        }
    }
}
