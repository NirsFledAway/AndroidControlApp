package com.tamerlanchik.robocar.transport;

import androidx.lifecycle.LiveData;

public interface  Communicator {
    void onStart();
    void onStop();
    void onDestroy();
    void onResume();
    void onPause();

//    boolean send(String string);
//    boolean send(byte[] message);
    boolean send(Package data);
    LiveData<Event> getOnEventChan();

    boolean disconnect();
    boolean isConnected();
}

