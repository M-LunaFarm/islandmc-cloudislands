package kr.lunaf.cloudislands.velocity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kr.lunaf.cloudislands.common.json.JsonCodecException;
import org.junit.jupiter.api.Test;

class CoreEventJsonCodecTest {
    private final CoreEventCodec codec = new CoreEventJsonCodec();

    @Test
    void decodesCoreEventBatchWithoutRegexParserCoupling() {
        CoreEventBatch batch = codec.decodeBatch("""
            {
              "oldestSeq": 7,
              "latestSeq": 9,
              "events": [
                {
                  "seq": 8,
                  "type": "NODE_STATE_CHANGED",
                  "fields": {
                    "nodeId": "island-2",
                    "state": "SHUTDOWN_SAFE",
                    "operation": "drain:\\uC644\\uB8CC"
                  },
                  "occurredAt": "2026-06-19T00:00:00Z"
                }
              ]
            }
            """);

        assertEquals(7L, batch.oldestSequence());
        assertEquals(9L, batch.latestSequence());
        assertEquals(1, batch.events().size());
        CoreEventEnvelope event = batch.events().getFirst();
        assertEquals(8L, event.sequence());
        assertEquals("NODE_STATE_CHANGED", event.type());
        assertEquals("island-2", event.fields().get("nodeId"));
        assertEquals("SHUTDOWN_SAFE", event.fields().get("state"));
        assertEquals("drain:완료", event.fields().get("operation"));
        assertEquals("2026-06-19T00:00:00Z", event.occurredAt());
    }

    @Test
    void rejectsMalformedCoreEventBatchesInsteadOfReturningEmptyStreams() {
        assertThrows(JsonCodecException.class, () -> codec.decodeBatch(""));
        assertThrows(JsonCodecException.class, () -> codec.decodeBatch("[]"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":1,\"latestSeq\":2,\"events\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":\"x\",\"latestSeq\":2,\"events\":[]}"));
    }

    @Test
    void rejectsMalformedCoreEventEntriesInsteadOfSkippingOrCoercingThem() {
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":1,\"latestSeq\":2,\"events\":[\"bad\"]}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":1,\"latestSeq\":2,\"events\":[{\"seq\":1.5,\"type\":\"NODE_DOWN\",\"fields\":{},\"occurredAt\":\"2026-06-19T00:00:00Z\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":1,\"latestSeq\":2,\"events\":[{\"seq\":1,\"type\":\"\",\"fields\":{},\"occurredAt\":\"2026-06-19T00:00:00Z\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBatch("{\"oldestSeq\":1,\"latestSeq\":2,\"events\":[{\"seq\":1,\"type\":\"NODE_DOWN\",\"fields\":{\"nodeId\":{}},\"occurredAt\":\"2026-06-19T00:00:00Z\"}]}"));
    }
}
