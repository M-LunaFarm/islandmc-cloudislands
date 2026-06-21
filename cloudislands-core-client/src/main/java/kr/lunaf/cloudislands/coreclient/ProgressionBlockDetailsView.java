package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record ProgressionBlockDetailsView(String totalWorth, long totalLevelPoints, List<ProgressionBlockDetailView> blocks) {
    public ProgressionBlockDetailsView {
        totalWorth = totalWorth == null || totalWorth.isBlank() ? "0" : totalWorth;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
