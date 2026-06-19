package kr.lunaf.cloudislands.paper.platform.compatibility;

public final class DetectedPaperCapabilities implements PaperCapabilities {
    private final boolean regionScheduler;
    private final boolean dataComponents;
    private final boolean dialogApi;
    private final boolean registryMutation;

    public DetectedPaperCapabilities() {
        this(DetectedPaperCapabilities.class.getClassLoader());
    }

    DetectedPaperCapabilities(ClassLoader classLoader) {
        this.regionScheduler = classExists(classLoader, "io.papermc.paper.threadedregions.RegionizedServer")
            || classExists(classLoader, "io.papermc.paper.threadedregions.scheduler.RegionScheduler");
        this.dataComponents = classExists(classLoader, "io.papermc.paper.datacomponent.DataComponentType");
        this.dialogApi = classExists(classLoader, "io.papermc.paper.dialog.Dialog");
        this.registryMutation = classExists(classLoader, "io.papermc.paper.registry.RegistryAccess");
    }

    @Override
    public boolean supportsRegionScheduler() {
        return regionScheduler;
    }

    @Override
    public boolean supportsDataComponents() {
        return dataComponents;
    }

    @Override
    public boolean supportsMinorApiVersion() {
        return true;
    }

    @Override
    public boolean supportsDialogApi() {
        return dialogApi;
    }

    @Override
    public boolean supportsRegistryMutation() {
        return registryMutation;
    }

    private static boolean classExists(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
