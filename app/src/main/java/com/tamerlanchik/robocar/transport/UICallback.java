package com.tamerlanchik.robocar.transport;

public interface UICallback {
    public enum Event {Note, FAIL, ERROR};
    void handleStatus(Event event, String data);
}
