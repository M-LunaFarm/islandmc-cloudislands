package kr.lunaf.cloudislands.coreclient;

public record LevelView(String islandId, long level, String worth, String calculatedAt) {
    public LevelView {
        islandId = islandId == null ? "" : islandId;
        worth = worth == null ? "0" : worth;
        calculatedAt = calculatedAt == null ? "" : calculatedAt;
    }
}
