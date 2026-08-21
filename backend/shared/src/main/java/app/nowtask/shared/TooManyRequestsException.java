package app.nowtask.shared;

public class TooManyRequestsException extends RuntimeException implements ErrorCoded {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String errorCode() {
        return "RATE_LIMITED";
    }
}
