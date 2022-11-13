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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.List;

public class WifiController implements Communicator {
    static final String TAG = "WifiController";

    MutableLiveData<Event> mEventLiveData = new MutableLiveData<>();
    Statistics mSendStatistics = new Statistics();

    Socket mSocket;
    DatagramSocket mUdpSocket;
    String mDestinationHost;
    InetAddress mDestinationHostAddress;
    int mDestinationPort;
    Thread mListeningThread;
    boolean mSelectUdp;

    public WifiController(String host, int port, boolean selectUdp) {
        mDestinationHost = host;
        mDestinationPort = port;
        mSelectUdp = selectUdp;
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

    public boolean maintainTCPSocket() {
        if (mSocket == null) {
            mSocket = new Socket();
        }
        if (!mSocket.isConnected()) {
            long start = System.currentTimeMillis();
            try {
//                mSocket = new Socket(mDestinationHost, mDestinationPort);
//                mSocket.connect(new InetSocketAddress(mDestinationHost, mDestinationPort), 100);
                mSocket.connect(new InetSocketAddress(mDestinationHost, mDestinationPort));
                return true;
            } catch (Exception e) {
                handleError(new Exception("Cannot create ping socket"));
                Log.e(TAG, "Cannot create ping socket: " + (System.currentTimeMillis() - start));
                return false;
            }
        }
        return true;
    }

    public boolean maintainUPDSocket() throws SocketException {
        if (mUdpSocket == null) {
            mUdpSocket = new DatagramSocket(mDestinationPort);
        }
        if (!mUdpSocket.isConnected()) {
            long start = System.currentTimeMillis();
            try {
                mDestinationHostAddress = InetAddress.getByName(mDestinationHost);
//                mUdpSocket.connect(address, mDestinationPort);
//                return true;
            } catch (Exception e) {
                handleError(new Exception("Cannot create ping socket"));
                Log.e(TAG, "Cannot create ping socket: " + (System.currentTimeMillis() - start));
                return false;
            }
        }
        return true;
    }
    public boolean maintainSocket() {
        boolean res;
        if (mSelectUdp) {
            try {
                res = maintainUPDSocket();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            res = maintainTCPSocket();
        }
        if (!res) {
            return res; // TODO
        }
//        if (mListeningThread != null) {
//            mListeningThread.interrupt();
//        }

        if (mListeningThread == null) {

            if (mSelectUdp) {
                mListeningThread = new Thread(()->{
                    listenUdp();
                });
            } else {
                mListeningThread = new Thread(()->{
                    listen();
                });
            }

            mListeningThread.setDaemon(true);
            mListeningThread.start();
        }

        return true;
    }

    @Override
    public boolean send(Package pkg) {
        if (!maintainSocket()) {
            return false;
        }
        try {
            ByteBuffer packed = ByteStuffingPackager.packWithByteStuffing(
                    ByteBuffer.wrap(pkg.getBinary())
            );
            if (mSelectUdp) {
                byte[] buff = packed.array();
                DatagramPacket packet = new DatagramPacket(buff, buff.length, mDestinationHostAddress, mDestinationPort);
                mUdpSocket.send(packet);
            } else {
                mSocket.getOutputStream().write(packed.array());
                mSocket.getOutputStream().flush();
            }
        } catch (IOException e) {
            handleError(new Exception("Cannot write ping to socket"));
            Log.e(TAG, "Cannot write ping to socket");
            mSocket = null;
            mUdpSocket = null;
            return false;
        }
        return true;
    }

    private void listenUdp() {
        while (mUdpSocket != null) {
            Log.d(TAG, "Client connected");
            ByteStuffingPackager packager = new ByteStuffingPackager();
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            int bytesRead = 0;
            while (bytesRead != -1) {
                try {
                    mUdpSocket.receive(packet);
                    bytesRead = packet.getLength();
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
