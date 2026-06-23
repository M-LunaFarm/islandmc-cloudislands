package kr.lunaf.cloudislands.paper.integration.spi;

public enum IntegrationSupportState {
    NOT_INSTALLED,
    DETECTED,
    API_INCOMPATIBLE,
    API_COMPATIBLE,
    ADAPTER_INACTIVE,
    ACTIVE,
    OPERATION_SUCCEEDED,
    OPERATION_FAILED,
    UNSUPPORTED;

    public static IntegrationSupportState operationState(IntegrationResult result) {
        if (result == null) {
            return OPERATION_FAILED;
        }
        return switch (result.status()) {
            case SUCCESS -> OPERATION_SUCCEEDED;
            case SKIPPED -> ADAPTER_INACTIVE;
            case FAILED -> OPERATION_FAILED;
        };
    }
}
