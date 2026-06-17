package kr.lunaf.cloudislands.common.security;

public final class BackendAccessPolicy {
    public static final String CONTRACT = "velocity-modern-forwarding-proxy-only-paper-backends";
    public static final String MODERN_FORWARDING_POLICY = "Velocity modern forwarding is required for Paper backend trust";
    public static final String FORWARDING_SECRET_POLICY = "forwarding secret must be configured on Velocity and Paper";
    public static final String PAPER_DIRECT_ACCESS_POLICY = "Paper island nodes reject non-proxy sources and require route sessions";
    public static final String PAPER_ONLINE_MODE_POLICY = "Paper online-mode=false is allowed only behind Velocity forwarding";
    public static final String INFRASTRUCTURE_EXPOSURE_POLICY = "Redis PostgreSQL and Object Storage stay private to the control plane";
    public static final String PLUGIN_MESSAGING_POLICY = "plugin messaging is never used for critical island lifecycle control";

    private BackendAccessPolicy() {
    }
}
