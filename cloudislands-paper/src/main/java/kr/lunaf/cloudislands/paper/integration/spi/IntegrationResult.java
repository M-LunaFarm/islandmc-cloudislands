package kr.lunaf.cloudislands.paper.integration.spi;

public record IntegrationResult(Status status, String message) {
    public IntegrationResult {
        status = status == null ? Status.SKIPPED : status;
        message = message == null ? "" : message;
    }

    public static IntegrationResult success(String message) {
        return new IntegrationResult(Status.SUCCESS, message);
    }

    public static IntegrationResult skipped(String message) {
        return new IntegrationResult(Status.SKIPPED, message);
    }

    public static IntegrationResult failed(String message) {
        return new IntegrationResult(Status.FAILED, message);
    }

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }
}
