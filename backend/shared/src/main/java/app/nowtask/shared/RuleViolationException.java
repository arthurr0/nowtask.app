package app.nowtask.shared;

public class RuleViolationException extends RuntimeException implements ErrorCoded {

    private final String code;

    public RuleViolationException(String message) {
        this(message, null);
    }

    public RuleViolationException(String message, String code) {
        super(message);
        this.code = code;
    }

    @Override
    public String errorCode() {
        return code;
    }
}
