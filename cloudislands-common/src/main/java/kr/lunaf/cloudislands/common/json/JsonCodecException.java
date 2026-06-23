package kr.lunaf.cloudislands.common.json;

public final class JsonCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        INVALID_JSON,
        INVALID_REQUEST
    }

    private final Kind kind;

    public JsonCodecException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public JsonCodecException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
