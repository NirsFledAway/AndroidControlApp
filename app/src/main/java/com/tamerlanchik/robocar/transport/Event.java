package com.tamerlanchik.robocar.transport;

public class Event {
    public enum EventType {RECEIVED, ERROR, CONNECTED, DISCONNECTED}
    public EventType mType;
    public Package mPackage;
    public Exception mException;

    public Event(EventType type) {
        mType = type;
    }

    public Event(Package data) {
        mPackage = data;
        mType = EventType.RECEIVED;
    }

    public Event(Exception e) {
        mException = e;
        mType = EventType.ERROR;
    }

    public Event(Package data, EventType type) {
        this(data);
        mType = type;
    }
}
