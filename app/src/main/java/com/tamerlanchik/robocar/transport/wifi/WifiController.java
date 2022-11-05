package com.tamerlanchik.robocar.transport.wifi;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.Package;
import com.tamerlanchik.robocar.transport.binary_tools.ByteStuffingPackager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

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

    public boolean maintainSocket() {
        if (mSocket == null) {
            mSocket = new Socket();
        }
        if (!mSocket.isConnected()) {
            long start = System.currentTimeMillis();
            try {
//                mSocket = new Socket(mDestinationHost, mDestinationPort);
                mSocket = new Socket();
                mSocket.connect(new InetSocketAddress(mDestinationHost, mDestinationPort), 100);
                Thread t = new Thread(()->{
                    listen();
                });
                t.start();
            } catch (Exception e) {
                handleError(new Exception("Cannot create ping socket"));
                Log.e(TAG, "Cannot create ping socket: " + (System.currentTimeMillis() - start));
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean send(Package pkg) {
        Log.e(TAG, "Message got");
        if (!maintainSocket()) {
            return false;
        }
        try {
            ByteBuffer packed = ByteStuffingPackager.packWithByteStuffing(
                    ByteBuffer.wrap(pkg.getBinary())
            );
            mSocket.getOutputStream().write(packed.array());
            mSocket.getOutputStream().flush();
//            socket.close();
        } catch (IOException e) {
            handleError(new Exception("Cannot write ping to socket"));
            Log.e(TAG, "Cannot write ping to socket");
            mSocket = null;
            return false;
        }
        return true;
    }

    private void listen() {
        InputStream in;
        while (mSocket != null && mSocket.isConnected()) {
            try {
                in = mSocket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return;
            }
            Log.d(TAG, "Client connected");
            ByteStuffingPackager packager = new ByteStuffingPackager();
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while (bytesRead != -1) {
                try {
                    bytesRead = in.read(buffer);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    bytesRead = -1;
                }
                if (bytesRead > 0) {
                    List<ByteBuffer> res = packager.unpackWithByteStuffing(ByteBuffer.wrap(buffer, 0, bytesRead));
                    if (res != null && res.size() > 0) {
                        Log.d(TAG, "Got new answer");
                        for(ByteBuffer frame : res) {
                            handleNewFrame(frame);
                        }
                    }
                }
            }
            Log.d(TAG, "Client disconnected");
        }
    }

    private void handleNewFrame(ByteBuffer frame) {
        Package pkg = new Package(frame.array(), frame.position());
        mEventLiveData.postValue(new Event(pkg, Event.EventType.RECEIVED));
    }

    private void handleError(Exception err) {
        mEventLiveData.postValue(new Event(err));
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
