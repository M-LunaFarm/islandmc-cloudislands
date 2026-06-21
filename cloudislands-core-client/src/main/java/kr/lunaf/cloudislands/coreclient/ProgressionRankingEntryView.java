package kr.lunaf.cloudislands.coreclient;

public record ProgressionRankingEntryView(String islandId, String name, long level, String worth, String valueKey) {
    public ProgressionRankingEntryView {
        islandId = islandId == null ? "" : islandId;
        name = name == null || name.isBlank() ? "이름 없는 섬" : name;
        worth = worth == null || worth.isBlank() ? "0" : worth;
        valueKey = valueKey == null ? "" : valueKey;
    }
}
