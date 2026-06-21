package kr.lunaf.cloudislands.coreclient;

public record AdminMaintenanceResultView(boolean reloaded, long clearedSessions, long clearedTickets, long clearedRedisKeys, String code) {
    public AdminMaintenanceResultView {
        code = code == null ? "" : code;
    }
}
