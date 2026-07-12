package kr.lunaf.cloudislands.coreservice.islandlog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdbcIslandLogRepositoryTest {
    @Test
    void payloadRoundTripPreservesPunctuationEscapesUnicodeAndControls() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("reason", "쉼표, 콜론: 따옴표 \"와 역슬래시 \\");
        payload.put("message", "첫 줄\n둘째 줄\t끝");
        payload.put("emoji", "섬🏝️");

        String encoded = JdbcIslandLogRepository.json(payload);

        assertEquals(payload, JdbcIslandLogRepository.parsePayload(encoded));
    }

    @Test
    void payloadRoundTripNormalizesNullsWithoutGeneratingInvalidJson() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("nullable", null);
        payload.put(null, "ignored");

        assertEquals(Map.of("nullable", ""), JdbcIslandLogRepository.parsePayload(JdbcIslandLogRepository.json(payload)));
        assertEquals(Map.of(), JdbcIslandLogRepository.parsePayload(JdbcIslandLogRepository.json(null)));
    }

    @Test
    void malformedOrNonObjectLegacyPayloadFailsClosedToEmptyMap() {
        assertEquals(Map.of(), JdbcIslandLogRepository.parsePayload("{broken"));
        assertEquals(Map.of(), JdbcIslandLogRepository.parsePayload("[\"not\",\"an\",\"object\"]"));
        assertEquals(Map.of(), JdbcIslandLogRepository.parsePayload(""));
        assertEquals(Map.of(), JdbcIslandLogRepository.parsePayload(null));
    }
}
