package kr.lunaf.cloudislands.coreclient;

import java.util.HashMap;
import java.util.Map;

public final class CoreApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;
    private final HashMap<String, String> details;
    private final String requestId;

    public CoreApiException(String code, String message) {
        this(code, message, 0);
    }

    public CoreApiException(String code, String message, int status) {
        this(code, message, status, Map.of(), "");
    }

    public CoreApiException(String code, String message, int status, Map<String, String> details, String requestId) {
        super(message == null || message.isBlank() ? code : message);
        this.code = code == null || code.isBlank() ? "CORE_API_ERROR" : code;
        this.status = status;
        this.details = new HashMap<>(details == null ? Map.of() : details);
        this.requestId = requestId == null ? "" : requestId;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public Map<String, String> details() {
        return Map.copyOf(details);
    }

    public String requestId() {
        return requestId;
    }
}
