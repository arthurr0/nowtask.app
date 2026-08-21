package app.nowtask.shared;

public class NoOrganizationException extends RuntimeException implements ErrorCoded {

    public NoOrganizationException() {
        super("The account does not belong to any organization");
    }

    @Override
    public String errorCode() {
        return "NO_ORGANIZATION";
    }
}
