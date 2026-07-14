package kr.lunaf.cloudislands.coreservice.job;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JobCompletionRequestTest {
    @Test
    void completionPayloadComparisonAcceptsDatabaseJsonNormalization() {
        assertTrue(JobCompletionRequest.completionPayloadMatches(
            "{ \"snapshotNo\" : \"42\", \"checksum\" : \"abc\" }",
            Map.of("checksum", "abc", "snapshotNo", "42")
        ));
        assertFalse(JobCompletionRequest.completionPayloadMatches(
            "{\"snapshotNo\":\"43\",\"checksum\":\"abc\"}",
            Map.of("checksum", "abc", "snapshotNo", "42")
        ));
    }
}
