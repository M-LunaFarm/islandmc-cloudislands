package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.ReviewModerationView;
import org.junit.jupiter.api.Test;

class VelocityAdminActionsTest {
    @Test
    void reviewModerationStateRejectsUnknownValuesAndSupportsOperatorAliases() {
        assertEquals("VISIBLE", VelocityAdminActions.reviewModerationState("restore"));
        assertEquals("REPORTED", VelocityAdminActions.reviewModerationState("pending"));
        assertEquals("HIDDEN", VelocityAdminActions.reviewModerationState("hide"));
        assertEquals("", VelocityAdminActions.reviewModerationState("typo"));
    }

    @Test
    void reviewModerationQueueKeepsActionableFullIdentifiers() {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        ReviewModerationView review = new ReviewModerationView(
            islandId.toString(),
            "Sky Home",
            reviewerUuid.toString(),
            "Reviewer",
            "REPORTED",
            3,
            "spam",
            "",
            "",
            "",
            "now"
        );

        String message = VelocityAdminActions.reviewModerationQueueMessage(List.of(review));

        assertTrue(message.contains(islandId.toString()));
        assertTrue(message.contains(reviewerUuid.toString()));
        assertTrue(message.contains("state=REPORTED"));
        assertTrue(message.contains("reports=3"));
    }
}
