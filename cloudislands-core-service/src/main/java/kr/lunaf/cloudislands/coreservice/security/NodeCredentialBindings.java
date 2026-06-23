package kr.lunaf.cloudislands.coreservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NodeCredentialBindings {
    private final Map<String, String> tokensByNode;

    public NodeCredentialBindings(Map<String, String> tokensByNode) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (tokensByNode != null) {
            tokensByNode.forEach((nodeId, token) -> {
                String safeNodeId = normalizeNodeId(nodeId);
                String safeToken = token == null ? "" : token.trim();
                if (!safeNodeId.isBlank() && !safeToken.isBlank()) {
                    copy.put(safeNodeId, safeToken);
                }
            });
        }
        this.tokensByNode = Map.copyOf(copy);
    }

    public static NodeCredentialBindings parse(String value) {
        if (value == null || value.isBlank()) {
            return new NodeCredentialBindings(Map.of());
        }
        LinkedHashMap<String, String> parsed = new LinkedHashMap<>();
        for (String entry : value.split("[,;\\n]")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 1 || separator == trimmed.length() - 1) {
                throw new IllegalArgumentException("Node credential entries must use nodeId:token");
            }
            String nodeId = normalizeNodeId(trimmed.substring(0, separator));
            String token = trimmed.substring(separator + 1).trim();
            if (nodeId.isBlank() || token.isBlank()) {
                throw new IllegalArgumentException("Node credential entries must include nodeId and token");
            }
            if (parsed.putIfAbsent(nodeId, token) != null) {
                throw new IllegalArgumentException("Duplicate node credential for " + nodeId);
            }
        }
        return new NodeCredentialBindings(parsed);
    }

    public boolean configured() {
        return !tokensByNode.isEmpty();
    }

    public boolean tokenMatches(String nodeId, String token) {
        String expected = tokensByNode.get(normalizeNodeId(nodeId));
        return constantTimeEquals(token, expected);
    }

    static String normalizeNodeId(String nodeId) {
        return nodeId == null ? "" : nodeId.trim();
    }

    private static boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
