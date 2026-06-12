package kr.lunaf.cloudislands.coreclient;

public final class CoreApiException extends RuntimeException {
    private final String code;
    private final int status;

    public CoreApiException(String code, String message) {
        this(code, message, 0);
    }

    public CoreApiException(String code, String message, int status) {
        super(message == null || message.isBlank() ? code : message);
        this.code = code == null || code.isBlank() ? "CORE_API_ERROR" : code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
