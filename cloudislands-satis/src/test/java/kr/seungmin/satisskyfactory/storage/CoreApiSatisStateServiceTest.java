package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiSatisStateServiceTest {
    @Test
    void reportsUnavailableWhenCloudIslandsApiIsMissing() {
        CoreApiSatisStateService service = new CoreApiSatisStateService(Logger.getLogger("test"), null, "cloudislands-satis");

        assertEquals("cloudislands-api-unavailable", service.writerReadiness());
        assertEquals("table-key-value-bulk-save-primary-with-flattened-state-fallback", service.writerTransportMode());
        assertEquals("queue-retry-then-flattened-addon-state", service.writerFallbackPolicy());
        assertTrue(service.flattenedFallbackEnabled());
        assertEquals(0, service.pendingBulkRetries());
    }

    @Test
    void reportsDisabledAddonStateFeatureBeforeWriting() {
        CoreApiSatisStateService service = new CoreApiSatisStateService(
                Logger.getLogger("test"),
                new NoopCloudIslandsApi(),
                "cloudislands-satis",
                false,
                feature -> !"addon-state".equals(feature)
        );

        assertEquals("addon-state-feature-disabled", service.writerReadiness());
        assertEquals("table-key-value-bulk-save-primary-no-flattened-fallback", service.writerTransportMode());
        assertEquals("queue-retry-only", service.writerFallbackPolicy());
        assertFalse(service.flattenedFallbackEnabled());
    }
}
