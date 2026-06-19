package kr.lunaf.cloudislands.paper.platform.compatibility;

public interface PaperCapabilities {
    boolean supportsRegionScheduler();

    boolean supportsDataComponents();

    boolean supportsMinorApiVersion();

    boolean supportsDialogApi();

    boolean supportsRegistryMutation();
}
