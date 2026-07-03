package kr.lunaf.cloudislands.paper.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import org.junit.jupiter.api.Test;

class CloudIslandsPlaceholderRanksTest {
    @Test
    void rendersWorthAndLevelRankPlaceholdersFromProgressionRankings() {
        String islandId = "00000000-0000-0000-0000-000000000601";
        CoreGuiViews.RankingData rankings = new CoreGuiViews.RankingData(
            List.of(
                new CoreGuiViews.RankingView(1, "level", "00000000-0000-0000-0000-000000000111", 20L, "2000.00"),
                new CoreGuiViews.RankingView(2, "level", "00000000-0000-0000-0000-000000000222", 15L, "1500.00"),
                new CoreGuiViews.RankingView(3, "level", islandId, 12L, "1234.50")
            ),
            List.of(
                new CoreGuiViews.RankingView(1, "worth", "00000000-0000-0000-0000-000000000111", 20L, "2000.00"),
                new CoreGuiViews.RankingView(2, "worth", islandId, 12L, "1234.50")
            ),
            List.of()
        );

        assertEquals(2, CloudIslandsPlaceholderRanks.worthRank(rankings, islandId));
        assertEquals(3, CloudIslandsPlaceholderRanks.levelRank(rankings, islandId));
        assertEquals(0, CloudIslandsPlaceholderRanks.worthRank(rankings, "missing"));
    }
}
