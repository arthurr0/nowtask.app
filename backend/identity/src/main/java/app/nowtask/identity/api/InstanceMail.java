package app.nowtask.identity.api;

public interface InstanceMail {
    record Result(boolean sent, String detail) {
    }

    boolean configured();

    Result sendTest(String to);
}
