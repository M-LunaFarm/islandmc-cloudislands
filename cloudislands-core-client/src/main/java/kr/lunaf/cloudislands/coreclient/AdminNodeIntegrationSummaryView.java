package kr.lunaf.cloudislands.coreclient;

public record AdminNodeIntegrationSummaryView(String text, long nodeCount, long detectedCount, long missingCount, String policy) {
    public AdminNodeIntegrationSummaryView {
        text = text == null ? "" : text;
        policy = policy == null ? "" : policy;
    }
}
