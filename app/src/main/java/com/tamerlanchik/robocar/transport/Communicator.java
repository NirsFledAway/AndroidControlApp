package com.tamerlanchik.robocar.transport;

public interface  Communicator {
    void onStart();
    void onStop();
    void onDestroy();
    void onResume();
    void onPause();

    boolean send(String string);
    boolean send(byte[] message);

    boolean isConnected();
}

