package kr.lunaf.cloudislands.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CloudIslandsProvider {
    private static final AtomicReference<CloudIslandsApi> CURRENT = new AtomicReference<>();

    private CloudIslandsProvider() {}

    public static Optional<CloudIslandsApi> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CloudIslandsApi require() {
        CloudIslandsApi api = CURRENT.get();
        if (api == null) {
            throw new IllegalStateException("CloudIslands API is not available");
        }
        return api;
    }

    public static void set(CloudIslandsApi api) {
        if (api == null) {
            throw new IllegalArgumentException("api");
        }
        CURRENT.set(api);
    }

    public static void clear(CloudIslandsApi api) {
        if (api != null) {
            CURRENT.compareAndSet(api, null);
        }
    }
}
