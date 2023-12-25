package com.tamerlanchik.robocar.transport;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Инкапсулирует в себе логику Looper+Handler
// Асинхронно вызывает Communicator::send() в фоновом потоке
public class SendMediator extends HandlerThread {
    static final String TAG = "CommunicationMediator";
    static final int MESSAGE_OUTPUT = 0;
//    HashMap<String, Package> mSendQueue = new HashMap<>();

    Handler mHandler;
//    ConcurrentMap<String, T> mMessageMap = new ConcurrentHashMap<>();

    Communicator mCommunicator;

    public SendMediator(Communicator communicatorImplementation) {
        super(TAG);
        mCommunicator = communicatorImplementation;
    }

    @Override
    public boolean quit() {
        return super.quit();
    }

    public void enqueueOutput(Package pkg, String key) {
        if (pkg == null) {
            return;
        }
//        if (mSendQueue.containsKey(pkg.getKey())) {
//            return;
//        }
//        mSendQueue.put(pkg.getKey(), pkg);
        // Взять новое сообщение из пула свободных и заполнить его
        Message message = mHandler.obtainMessage(MESSAGE_OUTPUT, pkg);
        // Положить сообщение в очередь
        message.sendToTarget();
    }

    @Override
    protected void onLooperPrepared() {
//        or Looper.getMainLooper()
        mHandler = new Handler(this.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_OUTPUT) {
                    Package pkg = (Package) msg.obj;
                    doSendMessage(pkg);
//                    mSendQueue.remove(pkg.getKey());
                }
            }
        };
    }

    private void doSendMessage(final Package pkg) {
        if (! mCommunicator.send(pkg)) {
            Log.e(TAG, "Fail send package " + pkg.getKey() );
        }
    }
}
