package kr.lunaf.cloudislands.coreservice;

public record RouteAccessDecision(boolean allowed, int status, String code, String message) {
    public static RouteAccessDecision granted() {
        return new RouteAccessDecision(true, 202, "", "");
    }

    public static RouteAccessDecision rejected(int status, String code, String message) {
        return new RouteAccessDecision(false, status, code, message);
    }
}
