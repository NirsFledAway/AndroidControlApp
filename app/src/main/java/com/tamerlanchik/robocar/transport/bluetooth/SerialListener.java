package com.tamerlanchik.robocar.transport.bluetooth;

public interface  SerialListener {
    void onSerialConnect      (boolean connected);
    void onSerialConnectError (Exception e);
    void onSerialRead         (byte[] data);
    void onSerialIoError      (Exception e);
}
