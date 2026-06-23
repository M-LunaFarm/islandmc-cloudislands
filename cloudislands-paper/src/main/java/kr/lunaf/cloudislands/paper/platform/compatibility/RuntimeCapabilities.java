package kr.lunaf.cloudislands.paper.platform.compatibility;

public record RuntimeCapabilities(
    boolean scheduler,
    boolean worldLifecycle,
    boolean registryAccess,
    boolean playerTransfer,
    boolean pluginMessaging,
    boolean bundleRestore
) {
    public static RuntimeCapabilities baseline() {
        return new RuntimeCapabilities(true, true, true, true, true, true);
    }

    public boolean completeForIslandNode() {
        return scheduler && worldLifecycle && registryAccess && playerTransfer && pluginMessaging && bundleRestore;
    }

    public String summary() {
        return "scheduler=" + scheduler
            + ",worldLifecycle=" + worldLifecycle
            + ",registryAccess=" + registryAccess
            + ",playerTransfer=" + playerTransfer
            + ",pluginMessaging=" + pluginMessaging
            + ",bundleRestore=" + bundleRestore;
    }
}
