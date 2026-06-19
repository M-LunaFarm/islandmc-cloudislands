package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;

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

    public String runtimeCellSuffix(String object) {
        if (object == null || object.contains("\"cellX\":null") || object.contains("\"cellZ\":null")) {
            return "";
        }
        return hideNodeNames ? "" : " cell=" + longValue(object, "cellX") + "," + longValue(object, "cellZ");
    }

    public String nodeIslandRuntimeSuffix(String object) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String state = jsonValue(object, "state");
        if (!state.isBlank()) {
            parts.add(state);
        }
        String world = jsonValue(object, "activeWorld");
        if (!world.isBlank()) {
            parts.add("world=" + world);
        }
        if (object != null && !object.contains("\"cellX\":null") && !object.contains("\"cellZ\":null")) {
            parts.add("cell=" + longValue(object, "cellX") + "," + longValue(object, "cellZ"));
        }
        return String.join(" ", parts);
    }
}
