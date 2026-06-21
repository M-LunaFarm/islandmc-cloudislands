package kr.lunaf.cloudislands.coreclient;

public record AdminNodeSummaryView(String text) {
    public AdminNodeSummaryView {
        text = text == null ? "" : text;
    }
}
