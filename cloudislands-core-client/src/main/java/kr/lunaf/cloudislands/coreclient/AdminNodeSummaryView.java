package kr.lunaf.cloudislands.coreclient;

public record AdminNodeSummaryView(String text, long nodeCount, long routeCandidateCount, long staleNodeCount, long heartbeatTimeoutSeconds) {
    public AdminNodeSummaryView(String text) {
        this(text, 0L, 0L, 0L, 0L);
    }

    public AdminNodeSummaryView {
        text = text == null ? "" : text;
    }
}
