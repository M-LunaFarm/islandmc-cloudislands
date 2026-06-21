package kr.lunaf.cloudislands.coreclient;

public record AdminIslandRuntimeView(
    String islandId,
    String state,
    String activeNode,
    String activeWorld,
    Long cellX,
    Long cellZ,
    String leaseOwner,
    long fencingToken,
    String activatedAt,
    String lastHeartbeat,
    String code
) {
    public AdminIslandRuntimeView {
        islandId = islandId == null ? "" : islandId;
        state = state == null ? "" : state;
        code = code == null ? "" : code;
    }

    public boolean hasCell() {
        return cellX != null && cellZ != null;
    }
}
