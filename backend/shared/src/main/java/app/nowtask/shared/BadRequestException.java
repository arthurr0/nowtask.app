package app.nowtask.shared;

public class BadRequestException extends RuntimeException implements ErrorCoded {

    private final String code;

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    @Override
    public String errorCode() {
        return code;
    }
}
