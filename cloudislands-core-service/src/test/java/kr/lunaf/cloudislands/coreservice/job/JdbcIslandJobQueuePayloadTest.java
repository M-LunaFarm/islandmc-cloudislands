package kr.lunaf.cloudislands.coreservice.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcIslandJobQueuePayloadTest {
    @Test
    void jobPayloadRoundTripPreservesRealWorldPathAndReasonValues() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("storagePath", "islands/world,archive:2026/snapshot \"A\".zip");
        payload.put("reason", "관리자 요청: 복원, 검증 후 이동 🏝️");
        payload.put("metadata", "line1\nline2\\tail");

        assertEquals(payload, JdbcIslandJobQueue.payload(JdbcIslandJobQueue.toJson(payload)));
    }

    @Test
    void jobPayloadNormalizesNullsAndRejectsMalformedLegacyJson() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("nullable", null);
        payload.put(null, "ignored");

        assertEquals(Map.of("nullable", ""), JdbcIslandJobQueue.payload(JdbcIslandJobQueue.toJson(payload)));
        assertEquals(Map.of(), JdbcIslandJobQueue.payload(JdbcIslandJobQueue.toJson(null)));
        assertEquals(Map.of(), JdbcIslandJobQueue.payload("{broken"));
        assertEquals(Map.of(), JdbcIslandJobQueue.payload("[1,2,3]"));
    }

    @Test
    void uuidMappingSupportsPostgresUuidAndMysqlTextColumns() {
        UUID id = UUID.randomUUID();

        assertEquals(id, JdbcIslandJobQueue.uuid(id));
        assertEquals(id, JdbcIslandJobQueue.uuid(id.toString()));
        assertNull(JdbcIslandJobQueue.uuid(null));
        assertNull(JdbcIslandJobQueue.uuid(" "));
    }
}
