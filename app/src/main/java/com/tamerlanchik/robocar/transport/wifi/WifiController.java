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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;

public class WifiController implements Communicator {
    static final String TAG = "WifiController";

    MutableLiveData<Event> mEventLiveData = new MutableLiveData<>();
    Statistics mSendStatistics = new Statistics();

    UnionSocket mUSocket;
    String mDestinationHost;
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

    public boolean maintainSocket() {
        if (mUSocket == null) {
            try {
                mUSocket = mSelectUdp
                        ? UnionSocket.createUDP(mDestinationHost, mDestinationPort)
                        : UnionSocket.createTCP(mDestinationHost, mDestinationPort);
                mUSocket.connect();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
//        if (mListeningThread != null) {
//            mListeningThread.interrupt();
//        }

            if (mListeningThread == null) {
                mListeningThread = new Thread(this::listen);
                mListeningThread.setDaemon(true);
                mListeningThread.start();
            }
        }
        return true;
    }

    @Override
    public boolean send (Package pkg){
        if (!maintainSocket()) {
            return false;
        }
        try {
            ByteBuffer packed = ByteStuffingPackager.packWithByteStuffing(
                    ByteBuffer.wrap(pkg.getBinary())
            );
            mUSocket.send(packed.array());
        } catch (IOException e) {
            handleError(new Exception("Cannot write ping to socket"));
            Log.e(TAG, "Cannot write ping to socket");
            mUSocket = null;
            return false;
        }
        return true;
    }

    private void listen () {
        while (mUSocket != null && mUSocket.isActive()) {
            Log.d(TAG, "Client connected");
            try {
                mUSocket.newClient();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            ByteStuffingPackager packager = new ByteStuffingPackager();
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while (bytesRead != -1) {
                try {
                    bytesRead = mUSocket.receive(buffer);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    bytesRead = -1;
                }
                if (bytesRead > 0) {
                    List<ByteBuffer> res = packager.unpackWithByteStuffing(ByteBuffer.wrap(buffer, 0, bytesRead));
                    if (res != null && res.size() > 0) {
                        Log.d(TAG, "Got new answer");
                        for (ByteBuffer frame : res) {
                            handleNewFrame(frame);
                        }
                    }
                }
            }
            Log.d(TAG, "Client disconnected");
        }
    }

    private void handleNewFrame (ByteBuffer frame){
        Package pkg = new Package(frame.array(), frame.position());
        mEventLiveData.postValue(new Event(pkg, Event.EventType.RECEIVED));
    }

    private void handleError (Exception err){
        mEventLiveData.postValue(new Event(err));
    }

    @Override
    public LiveData<Event> getOnEventChan () {
        return mEventLiveData;
    }

    @Override
    public boolean disconnect () {
        return false;
    }

    @Override
    public boolean isConnected () {
        return false;
    }
}

class Statistics {
    public int mSentTotal = 0;
    public int mSentOK = 0;
}

class UnionSocket {
    Socket mTcpSocket;
    InputStream mInputStream;

    DatagramSocket mUdpSocket;
    boolean useUDP = false;
    String mHost;
    InetAddress mHostAddr;
    int mPort;

    private UnionSocket(String host, int port) {
        mHost = host;
        mPort = port;
    }

    public static UnionSocket createUDP(String host, int port) throws SocketException, UnknownHostException {
        UnionSocket socket = new UnionSocket(host, port);
        socket.mUdpSocket = new DatagramSocket(socket.mPort);
        socket.mHostAddr = InetAddress.getByName(host);
        socket.useUDP = true;
        return socket;
    }

    public static UnionSocket createTCP(String host, int port) {
        UnionSocket socket = new UnionSocket(host, port);
        socket.mTcpSocket = new Socket();
        return socket;
    }

    public void send(byte[] data) throws IOException {
        if (useUDP) {
            DatagramPacket pkg = new DatagramPacket(data, data.length, mHostAddr, mPort);
            mUdpSocket.send(pkg);
        } else {
            mTcpSocket.getOutputStream().write(data);
            mTcpSocket.getOutputStream().flush();
        }
    }

    public int receive(byte[] buffer) throws IOException {
        if (useUDP) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            mUdpSocket.receive(packet);
            return packet.getLength();
        } else {
            return mInputStream.read(buffer);
        }
    }

    public void newClient() throws IOException {
        if (!useUDP) {
            mInputStream = mTcpSocket.getInputStream();
        }
    }

    public void connect() throws IOException {
        if (!useUDP) {
            mTcpSocket.connect(new InetSocketAddress(mHostAddr, mPort));
        }
    }

    public boolean isActive() {
        if (useUDP) {
            return true;
        } else {
            return mTcpSocket.isConnected();
        }
    }
}