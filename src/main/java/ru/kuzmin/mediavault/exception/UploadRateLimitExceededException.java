package ru.kuzmin.mediavault.exception;

public class UploadRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public UploadRateLimitExceededException(long retryAfterSeconds) {
        super("Too many upload requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
