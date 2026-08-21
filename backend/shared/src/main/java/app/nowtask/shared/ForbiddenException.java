package app.nowtask.shared;

public class ForbiddenException extends RuntimeException implements ErrorCoded {

    private final String code;
    private final String detail;

    public ForbiddenException(String code, String detail) {
        super(detail == null ? code : code + ": " + detail);
        this.code = code;
        this.detail = detail;
    }

    @Override
    public String errorCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }

    public static ForbiddenException missingPermission(Permission permission) {
        return new ForbiddenException("ROLE_FORBIDDEN", permission.code());
    }
}
