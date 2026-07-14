package kr.lunaf.cloudislands.paper.bootstrap;

public final class PaperBootstrapException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PaperBootstrapException(String message) {
        super(message);
    }

    public PaperBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
