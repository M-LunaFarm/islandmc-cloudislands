package kr.lunaf.cloudislands.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CloudIslandsProvider {
    private static final AtomicReference<CloudIslandsApi> CURRENT = new AtomicReference<>();

    private CloudIslandsProvider() {}

    public static Optional<CloudIslandsApi> get() {
        CloudIslandsApi api = CURRENT.get();
        return api == null ? findBukkitServiceApi() : Optional.of(api);
    }

    public static CloudIslandsApi require() {
        return get().orElseThrow(() -> new IllegalStateException("CloudIslands API is not available"));
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

    private static Optional<CloudIslandsApi> findBukkitServiceApi() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object server = bukkit.getMethod("getServer").invoke(null);
            if (server == null) {
                return Optional.empty();
            }
            Object services = server.getClass().getMethod("getServicesManager").invoke(server);
            if (services == null) {
                return Optional.empty();
            }
            Object registration = services.getClass().getMethod("getRegistration", Class.class).invoke(services, CloudIslandsApi.class);
            if (registration == null) {
                return Optional.empty();
            }
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            return provider instanceof CloudIslandsApi api ? Optional.of(api) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return Optional.empty();
        }
    }
}
