package kr.lunaf.cloudislands.coreclient;

public record AdminRouteClearView(boolean clearedSession, boolean clearedTicket, String reason) {
    public AdminRouteClearView {
        reason = reason == null ? "" : reason;
    }
}
