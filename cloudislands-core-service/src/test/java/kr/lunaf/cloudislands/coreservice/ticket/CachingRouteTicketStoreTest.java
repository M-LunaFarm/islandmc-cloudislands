package kr.lunaf.cloudislands.coreservice.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import org.junit.jupiter.api.Test;

class CachingRouteTicketStoreTest {
    @Test
    void countsCacheParserPreservesValidCountsAndDefaultsMissingStates() {
        Map<String, Long> counts = CachingRouteTicketStore.countsFromJson("{\"PREPARING\":2,\"READY\":\"3\"}");

        assertEquals(2L, counts.get(RouteTicketState.PREPARING.name()));
        assertEquals(3L, counts.get(RouteTicketState.READY.name()));
        assertEquals(0L, counts.get(RouteTicketState.CONSUMED.name()));
    }

    @Test
    void countsCacheParserRejectsCorruptedCountsInsteadOfCoercingToZero() {
        assertThrows(IllegalArgumentException.class, () -> CachingRouteTicketStore.countsFromJson("{\"PREPARING\":\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> CachingRouteTicketStore.countsFromJson("{\"PREPARING\":1.5}"));
        assertThrows(IllegalArgumentException.class, () -> CachingRouteTicketStore.countsFromJson("{\"PREPARING\":-1}"));
        assertThrows(IllegalArgumentException.class, () -> CachingRouteTicketStore.countsFromJson("{\"PREPARING\":9223372036854775808}"));
    }
}
