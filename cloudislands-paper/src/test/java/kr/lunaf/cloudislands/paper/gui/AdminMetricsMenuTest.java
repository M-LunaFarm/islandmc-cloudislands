package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminMetricsMenuTest {
    @Test
    void operationalFocusMetricsRenderBeforeAlphabeticalRemainder() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("cloudislands_z_custom", 3.0D);
        values.put("cloudislands_jobs_pending", 2.0D);
        values.put("cloudislands_node_active_islands", 4.0D);
        values.put("cloudislands_a_custom", 1.0D);

        assertEquals(List.of(
            "cloudislands_node_active_islands",
            "cloudislands_jobs_pending",
            "cloudislands_a_custom",
            "cloudislands_z_custom"
        ), AdminMetricsMenu.orderedMetrics(values));
    }

    @Test
    void metricLoreFormatsFiniteValuesAndUnits() {
        List<String> seconds = AdminMetricsMenu.metricLore(
            "cloudislands_database_query_seconds",
            0.012500D,
            null
        );
        List<String> counter = AdminMetricsMenu.metricLore(
            "cloudislands_core_security_rejects_total",
            4.0D,
            null
        );

        assertTrue(seconds.stream().anyMatch(line -> line.contains("최신 값: 0.0125")));
        assertTrue(seconds.stream().anyMatch(line -> line.contains("단위: seconds")));
        assertTrue(counter.stream().anyMatch(line -> line.contains("최신 값: 4")));
        assertTrue(counter.stream().anyMatch(line -> line.contains("단위: counter")));
    }
}
