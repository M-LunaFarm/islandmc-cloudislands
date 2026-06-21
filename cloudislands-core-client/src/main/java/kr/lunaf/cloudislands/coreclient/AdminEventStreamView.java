package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record AdminEventStreamView(long oldestSeq, long latestSeq, List<AdminEventView> events) {
    public AdminEventStreamView {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
