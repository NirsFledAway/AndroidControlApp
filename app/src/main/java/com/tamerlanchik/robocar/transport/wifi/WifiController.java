package com.tamerlanchik.robocar.transport.wifi;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.Package;

public class WifiController implements Communicator {
    MutableLiveData<Event> mEventLiveData = new MutableLiveData<>();
    Statistics mSendStatistics = new Statistics();


    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public boolean send(Package data) {
        return false;
    }

    @Override
    public LiveData<Event> getOnEventChan() {
        return mEventLiveData;
    }

    @Override
    public boolean disconnect() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return false;
    }
}

class Statistics {
    public int mSentTotal = 0;
    public int mSentOK = 0;
}
