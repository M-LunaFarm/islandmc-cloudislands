package kr.lunaf.cloudislands.coreservice;

public record RoutePreparationResult(int status, String body) {
    public static RoutePreparationResult accepted(String body) {
        return new RoutePreparationResult(202, body);
    }

    public static RoutePreparationResult rejected(int status, String body) {
        return new RoutePreparationResult(status, body);
    }
}
