package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.List;

public record AdminMetricsSummaryView(long samples, List<String> names) {
    public AdminMetricsSummaryView {
        names = names == null ? List.of() : List.copyOf(names);
    }

    static AdminMetricsSummaryView parse(String body) {
        if (body == null || body.isBlank()) {
            return new AdminMetricsSummaryView(0L, List.of());
        }
        long samples = 0L;
        List<String> names = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            samples++;
            if (names.size() < 6) {
                int brace = trimmed.indexOf('{');
                int space = trimmed.indexOf(' ');
                int end = brace > 0 ? brace : space > 0 ? space : trimmed.length();
                String name = trimmed.substring(0, end);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return new AdminMetricsSummaryView(samples, names);
    }
}
