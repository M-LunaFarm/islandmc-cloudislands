package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.coreclient.ReviewActionView;
import kr.lunaf.cloudislands.coreclient.ReviewView;
import org.junit.jupiter.api.Test;

class VelocityPlayerProgressionActionsTest {
    @Test
    void reviewEntriesPreferReviewerNamesWithUuidFallback() {
        ReviewView named = new ReviewView("", "33333333-3333-3333-3333-333333333333", 5L, "great", "", "", " ReviewPlayer ");
        ReviewView legacy = new ReviewView("44444444-4444-4444-4444-444444444444", 4L, "");

        assertEquals("ReviewPlayer=5/5 great", VelocityPlayerProgressionActions.reviewEntry(named));
        assertEquals("44444444=4/5", VelocityPlayerProgressionActions.reviewEntry(legacy));
    }

    @Test
    void visitorStatsIncludeRecentVisitorNamesWithUuidFallback() {
        IslandVisitorStatsView stats = new IslandVisitorStatsView("", 3L, 2L, List.of(
            new IslandVisitorStatsView.RecentVisitorView("33333333-3333-3333-3333-333333333333", "now", " VisitPlayer "),
            new IslandVisitorStatsView.RecentVisitorView("44444444-4444-4444-4444-444444444444", "later")
        ));

        assertEquals("방문 통계: total=3 unique=2 recent=VisitPlayer@now, 44444444@later", VelocityPlayerProgressionActions.visitorStatsMessage(stats));
    }

    @Test
    void reviewReportMessageIncludesModerationCountAndState() {
        assertEquals(
            "후기 신고 접수: 누적 신고=2 상태=REPORTED",
            VelocityPlayerProgressionActions.reviewReportMessage(new ReviewActionView(true, "REVIEW_REPORTED", "REPORTED", 2))
        );
    }
}
