package app.nowtask.integrations.api;

public interface Channels {

    record Delivery(boolean ok, String detail) {
    }

    Delivery notifyChannel(String channelName, String taskKey, String message);
}
