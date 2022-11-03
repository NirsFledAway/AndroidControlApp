package com.tamerlanchik.robocar.transport.wifi;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.Package;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;

public class WifiController implements Communicator {
    static final String TAG = "WifiController";

    MutableLiveData<Event> mEventLiveData = new MutableLiveData<>();
    Statistics mSendStatistics = new Statistics();

    Socket mSocket;
    String mDestinationHost;
    int mDestinationPort;

    public WifiController(String host, int port) {
        mDestinationHost = host;
        mDestinationPort = port;
    }


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
    public boolean send(Package pkg) {
        if (mSocket == null) {
            try {
                mSocket = new Socket(mDestinationHost, mDestinationPort);
            } catch (IOException e) {
                Log.e(TAG, "Cannot create ping socket");
                return false;

            }
        }
        try {
            ByteBuffer packed = packWithByteStuffing(ByteBuffer.wrap(pkg.getBinary()));
            byte[] bytearray = packed.array();
            mSocket.getOutputStream().write(packed.array());
            mSocket.getOutputStream().flush();
//            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot write ping to socket");
            mSocket = null;
            return false;
        }
        return true;
    }

//    private void listen() {
//        BufferedReader in;
//        try {
//            in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
//        } catch (IOException e) {
//            Log.e(TAG, e.getMessage());
//            return;
//        }
//
//        while (true) {
//            int label;
//            try {
//                label = in.read();
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//            }
//            switch(label) {
//                case
//            }
//        }
//    }

    private ByteBuffer packWithByteStuffing(ByteBuffer src) {
        final byte borderByte = 0x7E;
        final byte escapeByte = 0x7D;
        int len = src.limit();
        ByteBuffer dest = ByteBuffer.allocate(len+2);
        dest.put(borderByte);
        for (int i = 0; i < len; ++i) {
            byte srcByte = src.get(i);
            switch (srcByte) {
                case borderByte:
                    dest.put(escapeByte);
                    dest.put((byte)0x5E);
                    break;
                case escapeByte:
                    dest.put(escapeByte);
                    dest.put((byte)0x5D);
                    break;
                default:
                    dest.put(srcByte);
            }
        }

        dest.put(borderByte);
        return dest;
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
