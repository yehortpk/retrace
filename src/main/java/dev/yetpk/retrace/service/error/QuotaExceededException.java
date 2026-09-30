package dev.yetpk.retrace.service.error;

/**
 * A batch of uploads would take a project past its artifact storage quota. Carries both figures so
 * the caller can say how much was asked for and how much is left rather than just refusing.
 */
public class QuotaExceededException extends RuntimeException {

    private final long requestedBytes;
    private final long remainingBytes;

    public QuotaExceededException(long requestedBytes, long remainingBytes) {
        super("Storing %d bytes would exceed the project's artifact storage quota; %d bytes remain"
                .formatted(requestedBytes, remainingBytes));
        this.requestedBytes = requestedBytes;
        this.remainingBytes = remainingBytes;
    }

    public long getRequestedBytes() {
        return requestedBytes;
    }

    public long getRemainingBytes() {
        return remainingBytes;
    }
}
