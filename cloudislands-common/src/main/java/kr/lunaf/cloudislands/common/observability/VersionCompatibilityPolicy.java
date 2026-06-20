package kr.lunaf.cloudislands.common.observability;

import java.util.List;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;

public final class VersionCompatibilityPolicy {
    public static final String CLOUDISLANDS_RUNTIME_VERSION = "1.0.1";
    public static final String API_RUNTIME_VERSION = CloudIslandsApiContract.RUNTIME_API_VERSION;
    public static final String SUPPORTED_JAVA_VERSION = "21";
    public static final String SUPPORTED_PAPER_VERSION = "1.21.11";
    public static final String SUPPORTED_VELOCITY_VERSION = "3.5.0-SNAPSHOT";
    public static final String FOLIA_SUPPORT_POLICY = "folia-is-not-supported-until-paper-runtime-thread-affinity-and-region-scheduling-contracts-are-certified";
    public static final String MINECRAFT_UPDATE_POLICY = "paper-minor-updates-require-api-compile-smoke-and-island-runtime-certification-before-support";
    public static final String PROTOCOL_CHANGE_POLICY = "protocol-schema-n-must-read-n-minus-one-during-rolling-upgrade-and-breaking-wire-changes-require-major-runtime-bump";
    public static final String ROLLING_UPGRADE_POLICY = "upgrade-core-compatible-first-then-velocity-then-drain-and-restart-paper-nodes-with-protocol-n-minus-one-write-fencing";
    public static final String MINOR_COMPATIBILITY_POLICY = "minor-cloudislands-versions-keep-api-binary-compatibility-and-runtime-protocol-n-minus-one-for-one-minor-window";

    private static final List<String> ROLLING_UPGRADE_ORDER = List.of(
        "preflight-version-matrix",
        "core-compatible-first",
        "verify-core-schema-and-protocol-n-minus-one",
        "upgrade-velocity",
        "drain-one-paper-node",
        "upgrade-drained-paper-node",
        "post-node-route-save-smoke",
        "repeat-paper-drain-upgrade",
        "post-upgrade-multi-node-smoke"
    );

    private static final List<VersionCompatibilityRow> MATRIX = List.of(
        new VersionCompatibilityRow(
            "core-1.1-to-paper-agent-1.0",
            "Core 1.1",
            "Paper Agent 1.0",
            "compatible-with-write-fencing",
            "Paper may read snapshots and serve routes, but stale save writes require current runtime fencing token"
        ),
        new VersionCompatibilityRow(
            "core-1.1-to-velocity-1.0",
            "Core 1.1",
            "Velocity 1.0",
            "compatible",
            "Route tickets and presence payloads remain schema N-1 compatible during rolling upgrade"
        ),
        new VersionCompatibilityRow(
            "protocol-schema-n-to-n-minus-one",
            "Protocol schema N",
            "Protocol schema N-1",
            "compatible-for-one-minor-window",
            "Readers accept N-1 payloads and writers emit N until every Paper and Velocity agent is upgraded"
        ),
        new VersionCompatibilityRow(
            "paper-agent-newer-than-core",
            "Paper Agent newer minor",
            "Core older minor",
            "blocked-for-authority-writes",
            "Newer agents must not perform Core state mutations until Core advertises compatible metadata"
        ),
        new VersionCompatibilityRow(
            "folia-runtime",
            "Folia",
            "CloudIslands Paper runtime",
            "unsupported",
            FOLIA_SUPPORT_POLICY
        )
    );

    private VersionCompatibilityPolicy() {
    }

    public static List<VersionCompatibilityRow> matrix() {
        return MATRIX;
    }

    public static List<String> rollingUpgradeOrder() {
        return ROLLING_UPGRADE_ORDER;
    }

    public static String matrixSummary() {
        List<String> rows = new java.util.ArrayList<>();
        for (VersionCompatibilityRow row : MATRIX) {
            rows.add(row.key() + "=" + row.status());
        }
        return String.join(",", rows);
    }

    public static String rollingUpgradeOrderSummary() {
        return String.join(">", ROLLING_UPGRADE_ORDER);
    }

    public static boolean compatible(String key) {
        if (key == null) {
            return false;
        }
        return MATRIX.stream()
            .filter(row -> row.key().equals(key))
            .findFirst()
            .map(row -> row.status().startsWith("compatible"))
            .orElse(false);
    }

    public record VersionCompatibilityRow(
        String key,
        String producer,
        String consumer,
        String status,
        String contract
    ) {}
}
