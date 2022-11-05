package com.tamerlanchik.robocar;

import android.content.Context;
import android.graphics.Point;
import android.os.Message;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.tamerlanchik.robocar.control_screen.ControlActivity;
import com.tamerlanchik.robocar.control_screen.ControlsLivedataDispatcher;
import com.tamerlanchik.robocar.control_screen.MessageManager;
import com.tamerlanchik.robocar.control_screen.TaskScheduler;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.SendMediator;
import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.binary_tools.MessageBuilderV1;
import com.tamerlanchik.robocar.transport.Package;
import com.tamerlanchik.robocar.transport.bluetooth.SerialListener;
import com.tamerlanchik.robocar.transport.wifi.WifiController;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CommunicationHandler implements SerialListener, LifecycleObserver {
    static final String TAG = "CommunicationHandler";
    Context mContext;
    LogStorage mLogger;
    MessageManager mMessagemanager;
    ControlsLivedataDispatcher mControlsDispatcher;

    Communicator mCommunicator;
    boolean hexEnabled = false;
    boolean isConnected = false;
    String newline = TextUtil.newline_crlf;
    Socket mSocket;
    SendMediator<Package> mCommunicatorMediator;
    ConnectionChecker mConnectionChecker = new ConnectionChecker();

    MutableLiveData<Message> mEventLiveData = new MutableLiveData<>();

    public enum Connected { False, Pending, True }

//    cmd labels. From 0x00 to 0xff (1 byte)
    static final int PING_LABEL = 47;
    static final int JOYSTICKS_LABEL = 0x10;

// exported event codes
    public static final int CONNECTION_STATUS_CHANGED = 1;

    public CommunicationHandler(AppCompatActivity context, String deviceAddress) {
        mContext = context;
//        mCommunicator = new BluetoothController(context, deviceAddress);
//        mCommunicator = new WifiController("10.0.2.2",8082);
        mCommunicator = new WifiController("192.168.50.133",8082);
        mLogger = new ViewModelProvider(context).get(LogStorage.class);

        mCommunicatorMediator = new SendMediator<>(mCommunicator);
        mCommunicatorMediator.start();
        mCommunicatorMediator.getLooper();

        mControlsDispatcher = new ViewModelProvider(context).get(ControlsLivedataDispatcher.class);
        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, true).observe(context, (value)->{
            if (Boolean.valueOf(value)) {
                onResume();
                Timer t = new Timer();
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, false).postValue(Boolean.toString(mCommunicator.isConnected()));
                    }
                }, 1000*2);
            } else {
                onPause();
            }
        });

//        mControlsDispatcher.getData(ControlsLivedataDispatcher.PING_CMD, true).observe(context, (value)->{
//            int pingVal = (int)(Math.random() * 100 + 1);
//
//            ping(pingVal);
////            ping(Integer.getInteger(val));
//        });
        mCommunicator.getOnEventChan().observe(context, (event)-> {
            if (event.mType == Event.EventType.RECEIVED) {
                handleReceived(event.mPackage);
            } else if (event.mType == Event.EventType.ERROR) {
                mConnectionChecker.onError();
            }
        });

        initTasks();
    }

    private void handleReceived(Package pkg) {
        MessageBuilderV1.Message msg = MessageBuilderV1.unpack(pkg);
        switch(msg.label) {
            case PING_LABEL:
                Log.d(TAG, "Ping received: " + Integer.toString(msg.payload[0]));
                updateConnectionStatus(
                    mConnectionChecker.validateAnswer((int)msg.payload[0])
                    ? mConnectionChecker.getPingTime()
                    : -1
                );
                break;
            default:
                break;
        }

    }

    private void initTasks() {
        final int PING_INTERVAL = 300;
        TaskScheduler.get().addTask(TaskScheduler.TaskName.PING, PING_INTERVAL, ()->{
            Log.e(TAG, "Gonna check ping");
            int checkValue = mConnectionChecker.newCheck();
            if (checkValue > 0) {
                ping(checkValue);
            } else if (mConnectionChecker.raceDetected() == 1){ // prevent spam
                mLogger.write(new LogItem("Ping check race!", true));
            }
        });
        TaskScheduler.get().addTask(TaskScheduler.TaskName.PING_WATCHDOG, PING_INTERVAL*3, ()->{
            if (!mConnectionChecker.statusActive((int)(PING_INTERVAL*2.5))) {
                mConnectionChecker.onError();  // flush internal state
                updateConnectionStatus(-1);
            }
        });
    }

    private void updateConnectionStatus(boolean status) {
        // for indicate in log if fast flaps take place
        if (isConnected && !status) {
            mLogger.write(new LogItem("Connection lost", true));
        } else if (!isConnected && status) {
            mLogger.write(new LogItem("Connection established"));
        }
        isConnected = status;
    }

    private void updateConnectionStatus(long ping) {
        updateConnectionStatus(ping >= 0);
        mEventLiveData.postValue(Message.obtain(null, CONNECTION_STATUS_CHANGED, ping));
    }

    public void ping(int value) {
//        String data = Integer.toString((int)(Math.random() * 100 + 1));
        ByteBuffer data = ByteBuffer.allocate(1);
        data.put((byte)value);
        Log.e(TAG, "ping()");
        try {
//            new Thread(() -> {
//                try {
//                    sendOnQueue(MessageBuilderV1.build(PING_LABEL, data.array()).setKey(PING_LABEL));
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }).start();
            try {
                sendOnQueue(MessageBuilderV1.build(PING_LABEL, data.array()).setKey(PING_LABEL));
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e)
        {
            Log.e(TAG, "Bad ping label");
        }
    }

    public void sendOnQueue(Package pkg) {
        mCommunicatorMediator.enqueueOutput(pkg, pkg.getKey());

//        mCommunicator.send(pkg);
    }

    public boolean send(String str) {
        if (!mCommunicator.isConnected()) {
//        if(connected != Connected.True) {
            mLogger.write(new LogItem("Not connected", true));
            return false;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
//            SpannableStringBuilder spn = new SpannableStringBuilder(msg+'\n');
//            spn.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
//            mLogger.write("Gonna send: " + spn.toString());
            mCommunicator.send(new Package(data));
//            service.write(data);
//            mLogger.write(spn.toString());
        } catch (Exception e) {
            onSerialIoError(e);
            return false;
        }
        return true;
    }

    public void sendJoysticks(List<Point> values) {
//        ByteBuffer buffer = ByteBuffer.allocate(4*4);
//        for (int i = 0; i < 2; ++i) {
//            Point p = values.get(i);
//            buffer.putInt(p.x, p.y);
//        }
//        try {
//            send(MessageBuilderV1.build(JOYSTICKS_LABEL, buffer.array()));
//        } catch (Exception e)
//        {
//            Log.e(TAG, "Bad joystick label");
//        }
    }


    public void receive(byte[] data) {
        mMessagemanager.handleMessage(data);
    }

    @Override
    public void onSerialConnect(boolean connected) {
        if (connected) {
            mLogger.write("Serial connected");
        } else {
            mLogger.write("Serial disconnected");
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mLogger.write(new LogItem("Connecton failed: " + e.getMessage(), true));
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        mLogger.write(new LogItem("Connecion lost: " + e.getMessage(), true));
    }

    public void onDestroy() {
        mCommunicatorMediator.quit();
        mCommunicator.onDestroy();
    }

    public void onStart() {
        mCommunicator.onStart();
    }

    public void onStop() {
        mCommunicator.onStop();
    }

    public void onPause() {
        mLogger.write("Disconnecting...");
        mCommunicator.onPause();
    }

    public void onResume() {
        mLogger.write("Trying to reconnect...");
        mCommunicator.onResume();
    }
    public LiveData<Message> getOnEventChan() {
        return mEventLiveData;
    }

    class ConnectionChecker {
        int mLastSentValue = 0;
        boolean mCheckIsPending = false;
        int mRaceDetected = 0;
        long mLastChecked = 0;
        long mSentTimestamp = 0;
        long mPingTime = 0;
        long mPrevCheckCall = 0;

        static final int MAX_VAL = 100;
        static final int ADD_VALUE = 1;

        public int newCheck() {
            Log.e(TAG, "Check call interval: " + (System.currentTimeMillis() - mPrevCheckCall));
            mPrevCheckCall = System.currentTimeMillis();
            if (mCheckIsPending) {
                mRaceDetected = mRaceDetected < Integer.MAX_VALUE ? mRaceDetected+1 : 1;
                return -1;
            }
            mLastSentValue = (int)(Math.random() * MAX_VAL + 1);
            mSentTimestamp = System.currentTimeMillis();
            mCheckIsPending = true;
            return mLastSentValue;
        }

        public boolean validateAnswer(int answer) {
            if (!mCheckIsPending) {
                return false;
            }
            mCheckIsPending = false;
            mRaceDetected = 0;

            boolean res = answer == mLastSentValue + ADD_VALUE;
            if (res) {
                mPingTime = System.currentTimeMillis() - mSentTimestamp;
                mLastChecked = System.currentTimeMillis();
            }
            return res;
        }

        public boolean statusActive(long invervalTreshold) {
            long diff = System.currentTimeMillis() - mLastChecked;
            return diff <= invervalTreshold;
        }

        public long getPingTime() {
            return mPingTime;
        }

        public void onError () {
            mCheckIsPending = false;
        }

        public int raceDetected() {
            return mRaceDetected;
        }
    }
}
