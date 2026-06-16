package kr.lunaf.cloudislands.protocol;

import java.util.Map;

public final class ProtocolVersion {
    public static final int MIN_SUPPORTED = 1;
    public static final int CURRENT = 1;
    public static final String NEGOTIATION_POLICY = "reject-outside-supported-range-report-min-current-and-client-version";

    private ProtocolVersion() {
    }

    public static boolean supported(int clientVersion) {
        return clientVersion >= MIN_SUPPORTED && clientVersion <= CURRENT;
    }

    public static NegotiationResult negotiate(int clientVersion) {
        return new NegotiationResult(clientVersion, MIN_SUPPORTED, CURRENT, supported(clientVersion), NEGOTIATION_POLICY);
    }

    public record NegotiationResult(int clientVersion, int minSupported, int current, boolean accepted, String policy) {
        public Map<String, String> fields() {
            return Map.of(
                "clientVersion", Integer.toString(clientVersion),
                "minSupported", Integer.toString(minSupported),
                "current", Integer.toString(current),
                "accepted", Boolean.toString(accepted),
                "policy", policy
            );
        }
    }
}
