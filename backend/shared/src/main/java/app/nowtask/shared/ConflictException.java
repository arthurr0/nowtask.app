package app.nowtask.shared;

public class ConflictException extends RuntimeException implements ErrorCoded {

    private final String code;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    @Override
    public String errorCode() {
        return code;
    }
}
