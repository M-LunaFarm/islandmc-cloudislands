package kr.seungmin.satisskyfactory.storage;

public final class SatisStatePortabilityPolicy {
    public static final String AUTHORITY = "cloudislands-addon-state";
    public static final String NODE_BOUND = "false";
    public static final String PORTABILITY = "portable-across-island-nodes";
    public static final String RUNTIME_SOURCE = "CloudIslands IslandRuntime";
    public static final String REMAP_POLICY = "island-uuid-stable-active-world-and-center-volatile";
    public static final String REMAP_KEY = "islandUuid+activeWorld+activeCenter";
    public static final String WRITE_POLICY = "last-confirmed-state-wins";
    public static final String WRITE_FENCE = "active-island-runtime-owner-only";
    public static final String DUPLICATE_TICK_POLICY = "single-active-runtime-owner";
    public static final String NODE_HANDOFF_POLICY = "save-on-source-restore-on-target-by-island-uuid";
    public static final String ADDON_REMOVAL_POLICY = "preserve-cloudislands-island-and-addon-state-by-island-uuid";
    public static final String LOCAL_FALLBACK_RISK = "local-fallback-can-split-state-without-shared-backend";

    private SatisStatePortabilityPolicy() {
    }
}
