package kr.lunaf.cloudislands.coreclient;

public record ProgressionRankingIgnoreView(boolean accepted, String code, String islandId, boolean ignored) {
    public ProgressionRankingIgnoreView {
        code = code == null ? "" : code;
        islandId = islandId == null ? "" : islandId;
    }
}
