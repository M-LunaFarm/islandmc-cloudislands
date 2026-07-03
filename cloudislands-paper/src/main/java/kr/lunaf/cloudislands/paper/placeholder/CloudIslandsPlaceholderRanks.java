package kr.lunaf.cloudislands.paper.placeholder;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

final class CloudIslandsPlaceholderRanks {
    private CloudIslandsPlaceholderRanks() {
    }

    static int worthRank(CoreGuiViews.RankingData rankings, String islandId) {
        return rankOf(rankings == null ? null : rankings.worths(), islandId);
    }

    static int levelRank(CoreGuiViews.RankingData rankings, String islandId) {
        return rankOf(rankings == null ? null : rankings.levels(), islandId);
    }

    private static int rankOf(List<CoreGuiViews.RankingView> rankings, String islandId) {
        if (rankings == null || islandId == null || islandId.isBlank()) {
            return 0;
        }
        return rankings.stream()
            .filter(ranking -> islandId.equals(ranking.islandId()))
            .mapToInt(CoreGuiViews.RankingView::rank)
            .findFirst()
            .orElse(0);
    }
}
