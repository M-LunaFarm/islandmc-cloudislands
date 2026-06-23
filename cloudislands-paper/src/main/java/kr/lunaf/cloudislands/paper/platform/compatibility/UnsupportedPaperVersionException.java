package kr.lunaf.cloudislands.paper.platform.compatibility;

public final class UnsupportedPaperVersionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnsupportedPaperVersionException(String message) {
        super(message);
    }
}
