package kr.lunaf.cloudislands.coreclient;

public record JobView(String id, String type, String state, String targetNode, long attempts, String error) {
    public JobView {
        id = id == null ? "" : id;
        type = type == null ? "" : type;
        state = state == null ? "" : state;
        targetNode = targetNode == null ? "" : targetNode;
        error = error == null ? "" : error;
    }
}
