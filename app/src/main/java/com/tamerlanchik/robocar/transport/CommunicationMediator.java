package com.tamerlanchik.robocar.transport;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Инкапсулирует в себе логику Looper+Handler
public class CommunicationMediator<T> extends HandlerThread {
    static final String TAG = "CommunicationMediator";
    static final int MESSAGE_OUTPUT = 0;

    Handler mHandler;
//    ConcurrentMap<String, T> mMessageMap = new ConcurrentHashMap<>();

    Communicator mCommunicator;

    public CommunicationMediator(Communicator communicatorImplementation) {
        super(TAG);
        mCommunicator = communicatorImplementation;
    }

    @Override
    public boolean quit() {
        return super.quit();
    }

    public void enqueueOutput(T pkg, String key) {
        Log.i(TAG, "Added package to send");
        if (pkg == null) {
//            mMessageMap.remove(key);
            return;
        }
//        mMessageMap.put(key, pkg);
        Message message = mHandler.obtainMessage(MESSAGE_OUTPUT, pkg);
        message.sendToTarget();
    }

    @Override
    protected void onLooperPrepared() {
//        or Looper.getMainLooper()
        mHandler = new Handler(this.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_OUTPUT) {
                    T pkg = (T) msg.obj;
                    doSendMessage(pkg);
                }
            }
        };
    }

    private void doSendMessage(final T pkg) {
        doSendMessage((Package) pkg);
    }

    private void doSendMessage(final Package pkg) {
        if (! mCommunicator.send(pkg)) {
            Log.e(TAG, "Fail send package " + pkg.getKey() );
        }
    }
}
