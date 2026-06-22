package kr.lunaf.cloudislands.velocity.message;

public final class VelocityRoutePrivacyFormatter {
    private final boolean hideNodeNames;

    public VelocityRoutePrivacyFormatter(boolean hideNodeNames) {
        this.hideNodeNames = hideNodeNames;
    }

    public String hiddenNodeLabel(String nodeId) {
        if (hideNodeNames) {
            return "";
        }
        return nodeId == null || nodeId.isBlank() ? "" : " " + nodeId;
    }

    public String displayNodeName(String nodeId, int index) {
        if (hideNodeNames || nodeId == null || nodeId.isBlank()) {
            return "node-" + Math.max(1, index);
        }
        return nodeId;
    }

    public String routeNodeSuffix(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        return hideNodeNames ? "" : " node=" + nodeId;
    }

    public String routeRequestedNodeSuffix(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        return hideNodeNames ? "" : " requestedNode=" + nodeId;
    }

    public String routeServerSuffix(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return "";
        }
        return hideNodeNames ? "" : " server=" + serverName;
    }

    public String runtimeWorldSuffix(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "";
        }
        return hideNodeNames ? "" : " world=" + worldName;
    }

    public String runtimeCellSuffix(long cellX, long cellZ) {
        return hideNodeNames ? "" : " cell=" + cellX + "," + cellZ;
    }
}
