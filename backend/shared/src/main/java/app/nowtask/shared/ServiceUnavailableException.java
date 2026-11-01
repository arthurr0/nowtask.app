package app.nowtask.shared;

public class ServiceUnavailableException extends RuntimeException implements ErrorCoded {

    private final String code;

    public ServiceUnavailableException(String message, String code) {
        super(message);
        this.code = code;
    }

    @Override
    public String errorCode() {
        return code;
    }
}
