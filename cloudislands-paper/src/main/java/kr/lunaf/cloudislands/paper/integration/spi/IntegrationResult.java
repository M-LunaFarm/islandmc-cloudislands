package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Map;

public record IntegrationResult(Status status, String message, Map<String, String> details) {
    public IntegrationResult(Status status, String message) {
        this(status, message, Map.of());
    }

    public IntegrationResult {
        status = status == null ? Status.SKIPPED : status;
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static IntegrationResult success(String message) {
        return success(message, Map.of());
    }

    public static IntegrationResult success(String message, Map<String, String> details) {
        return new IntegrationResult(Status.SUCCESS, message, details);
    }

    public static IntegrationResult skipped(String message) {
        return skipped(message, Map.of());
    }

    public static IntegrationResult skipped(String message, Map<String, String> details) {
        return new IntegrationResult(Status.SKIPPED, message, details);
    }

    public static IntegrationResult failed(String message) {
        return failed(message, Map.of());
    }

    public static IntegrationResult failed(String message, Map<String, String> details) {
        return new IntegrationResult(Status.FAILED, message, details);
    }

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }
}
