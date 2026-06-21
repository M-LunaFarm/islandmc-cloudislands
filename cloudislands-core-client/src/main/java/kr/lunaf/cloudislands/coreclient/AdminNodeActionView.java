package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record AdminNodeActionView(boolean accepted, String code, String nodeId, String operation, List<String> nodes, int recoveryRequired) {
    public AdminNodeActionView(boolean accepted, String code, String nodeId, String operation) {
        this(accepted, code, nodeId, operation, List.of(), 0);
    }

    public AdminNodeActionView {
        code = code == null ? "" : code;
        nodeId = nodeId == null ? "" : nodeId;
        operation = operation == null ? "" : operation;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
