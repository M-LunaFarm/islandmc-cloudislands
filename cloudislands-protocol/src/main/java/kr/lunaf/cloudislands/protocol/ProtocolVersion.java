package kr.lunaf.cloudislands.protocol;

import java.util.Map;

public final class ProtocolVersion {
    public static final int MIN_SUPPORTED = 1;
    public static final int CURRENT = 1;
    public static final String NEGOTIATION_POLICY = "reject-outside-supported-range-report-min-current-and-client-version";
    public static final String HEARTBEAT_FIELD = "protocolVersion";
    public static final String UPGRADE_HINT = "upgrade-node-jar-or-core-service-to-overlapping-protocol-range";

    private ProtocolVersion() {
    }

    public static boolean supported(int clientVersion) {
        return clientVersion >= MIN_SUPPORTED && clientVersion <= CURRENT;
    }

    public static NegotiationResult negotiate(int clientVersion) {
        return new NegotiationResult(clientVersion, MIN_SUPPORTED, CURRENT, supported(clientVersion), NEGOTIATION_POLICY, HEARTBEAT_FIELD, status(clientVersion), UPGRADE_HINT);
    }

    public static String status(int clientVersion) {
        if (clientVersion < MIN_SUPPORTED) {
            return "client-too-old";
        }
        if (clientVersion > CURRENT) {
            return "client-too-new";
        }
        return "accepted";
    }

    public record NegotiationResult(int clientVersion, int minSupported, int current, boolean accepted, String policy, String field, String status, String upgradeHint) {
        public Map<String, String> fields() {
            return Map.of(
                "clientVersion", Integer.toString(clientVersion),
                "minSupported", Integer.toString(minSupported),
                "current", Integer.toString(current),
                "accepted", Boolean.toString(accepted),
                "policy", policy,
                "field", field,
                "status", status,
                "upgradeHint", upgradeHint
            );
        }
    }
}
