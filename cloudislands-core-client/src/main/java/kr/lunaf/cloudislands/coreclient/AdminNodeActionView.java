package kr.lunaf.cloudislands.coreclient;

public record AdminNodeActionView(boolean accepted, String code, String nodeId, String operation) {
    public AdminNodeActionView {
        code = code == null ? "" : code;
        nodeId = nodeId == null ? "" : nodeId;
        operation = operation == null ? "" : operation;
    }
}
