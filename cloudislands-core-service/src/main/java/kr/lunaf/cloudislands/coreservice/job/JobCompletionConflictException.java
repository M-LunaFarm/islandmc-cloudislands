package kr.lunaf.cloudislands.coreservice.job;

public final class JobCompletionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JobCompletionConflictException(String message) {
        super(message);
    }
}
