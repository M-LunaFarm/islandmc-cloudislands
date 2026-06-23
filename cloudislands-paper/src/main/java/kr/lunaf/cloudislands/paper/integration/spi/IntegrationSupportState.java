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
        return result.status() == IntegrationResult.Status.SUCCESS
            ? OPERATION_SUCCEEDED
            : OPERATION_FAILED;
    }
}
