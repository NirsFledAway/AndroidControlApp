package com.tamerlanchik.robocar;

import android.content.Context;
import android.graphics.Point;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkPayload;
import com.MAVLink.enums.MAV_TYPE;
import com.MAVLink.tamerlanchik.msg_heartbeat;
import com.tamerlanchik.robocar.control_screen.MessageManager;
import com.tamerlanchik.robocar.control_screen.TaskScheduler;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.SendMediator;
import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.binary_tools.ByteStuffingPackager;
import com.tamerlanchik.robocar.transport.binary_tools.MessageBuilderV1;
import com.tamerlanchik.robocar.transport.Package;
import com.tamerlanchik.robocar.transport.wifi.WifiController;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;

public class CommunicationHandler implements LifecycleEventObserver {
    static final String TAG = "CommunicationHandler";
    Context mContext;
    LogStorage mLogger;
    MessageManager mMessagemanager;
//    ControlsLivedataDispatcher mControlsDispatcher;

    Communicator mCommunicator;
    boolean hexEnabled = false;
    boolean isConnected = false;
    String newline = TextUtil.newline_crlf;
    Socket mSocket;
    SendMediator mCommunicatorMediator;
    ConnectionCheckHolder mConnectionChecker = new ConnectionCheckHolder();

    MutableLiveData<Message> mEventLiveData = new MutableLiveData<>();

//    cmd labels. From 0x00 to 0xff (1 byte)
    static final int PING_LABEL = 47;
    static final int JOYSTICKS_LABEL = 0x10;

// exported event codes
    public static final int CONNECTION_STATUS_CHANGED = 1;

    public CommunicationHandler(AppCompatActivity context, String targetAddress) {
        mContext = context;
//        mCommunicator = new BluetoothController(context, deviceAddress);
//        mCommunicator = new WifiController("10.0.2.2",8082);
        String[] address = targetAddress.split(":");
        if (address.length != 2) {
            throw new RuntimeException("Bad target address: " + targetAddress);
        }
        String host = address[0];
        int port = Integer.parseInt(address[1]);
        mCommunicator = new WifiController(host, port, mContext.getResources().getBoolean(R.bool.useUDP));
        mLogger = new ViewModelProvider(context).get(LogStorage.class);

        mCommunicatorMediator = new SendMediator(mCommunicator);
        mCommunicatorMediator.start();
        mCommunicatorMediator.getLooper();

//        mControlsDispatcher = new ViewModelProvider(context).get(ControlsLivedataDispatcher.class);
//        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, true).observe(context, (value)->{
//            if (Boolean.valueOf(value)) {
//                onResume();
//                Timer t = new Timer();
//                t.schedule(new TimerTask() {
//                    @Override
//                    public void run() {
//                        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, false).postValue(Boolean.toString(mCommunicator.isConnected()));
//                    }
//                }, 1000*2);
//            } else {
//                onPause();
//            }
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
        final int kPingInterval = mContext.getResources().getInteger(R.integer.ping_period);   // ms
        // TASK: ping
        TaskScheduler.get().addTask(TaskScheduler.TaskName.PING, kPingInterval, ()->{
            Log.e(TAG, "Gonna check ping");
            int checkValue = mConnectionChecker.newCheck();
            if (checkValue > 0) {
                ping(checkValue);
            } else if (mConnectionChecker.raceDetected() == 1){ // prevent spam
                mLogger.write(new LogItem("Ping check race!", true));
            }
        });
        // TASK: Ping watchdog
        TaskScheduler.get().addTask(TaskScheduler.TaskName.PING_WATCHDOG, 3*kPingInterval, ()->{
            if (!mConnectionChecker.statusActive((int)(kPingInterval*2.5))) {
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
        msg_heartbeat msg = new msg_heartbeat(
            (short) MAV_TYPE.MAV_TYPE_QUADROTOR,
            mConnectionChecker.newCheck(),
            3,
            200,
            true);
        MAVLinkPacket packet = msg.pack();
        byte[] encoded = packet.encodePacket();
//        byte[] encoded = "Hello".getBytes();

//        ByteBuffer data = ByteBuffer.allocate(1);
//        data.put((byte)value);
        Log.e(TAG, "ping()");
//      sendOnQueue(MessageBuilderV1.build(PING_LABEL, data.array()).setKey(PING_LABEL));
//        ByteBuffer bf = MessageBuilderV1.buildNoLength(PING_LABEL, data.array());
//        ByteStuffingPackager.packWithByteStuffing(bf);
        Package pkg = new Package(encoded);
        pkg.setKey(PING_LABEL);
        sendOnQueue(pkg);
    }

    public void sendOnQueue(Package pkg) {
        mCommunicatorMediator.enqueueOutput(pkg, pkg.getKey());
    }

//    public boolean send(String str) {
//        if (!mCommunicator.isConnected()) {
//            mLogger.write(new LogItem("Not connected", true));
//            return false;
//        }
//        try {
//            String msg;
//            byte[] data;
//            if(hexEnabled) {
//                StringBuilder sb = new StringBuilder();
//                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
//                TextUtil.toHexString(sb, newline.getBytes());
//                msg = sb.toString();
//                data = TextUtil.fromHexString(msg);
//            } else {
//                data = (str + newline).getBytes();
//            }
//            mCommunicator.send(new Package(data));
//        } catch (Exception e) {
//            onSerialIoError(e);
//            return false;
//        }
//        return true;
//    }

    public void sendJoysticks(List<Point> values) {
        final int INT_LENGTH = 2;
        ByteBuffer buffer = ByteBuffer.allocate(INT_LENGTH*values.size() * 2);
        for (Point p : values) {
//            buffer.putShort((short) -18);
//            byte[] a = buffer.array();
            buffer.putShort((short) p.x);
            buffer.putShort((short) p.y);
//            buffer.put(MessageBuilderV1.int2bytes(p.x, INT_LENGTH));
//            buffer.put(MessageBuilderV1.int2bytes(p.y, INT_LENGTH));
        }

        ByteBuffer bf = MessageBuilderV1.buildNoLength(JOYSTICKS_LABEL, buffer.array());
        ByteStuffingPackager.packWithByteStuffing(bf);
        Package pkg = new Package(bf.array());
        pkg.setKey(JOYSTICKS_LABEL);
        sendOnQueue(pkg);
    }

    public LiveData<Message> getOnEventChan() {
        return mEventLiveData;
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        switch(event) {
            case ON_START:
                mCommunicator.onStart(); break;
            case ON_RESUME:
                mCommunicator.onResume(); break;
            case ON_PAUSE:
                mCommunicator.onPause(); break;
            case ON_STOP:
                mCommunicator.onStop(); break;
            case ON_DESTROY:
                mCommunicatorMediator.quit();
                mCommunicator.onDestroy();
                break;
            default: break;
        }
    }

    // Хранит необходимые для проверки значения
    class ConnectionCheckHolder {
        short mLastSentValue = 0;
        boolean mCheckIsPending = false;


        // Если новый пинг начинается раньше, чем пришел ответ предыдущего
        int mRaceDetected = 0;
        long mLastChecked = 0;
        long mSentTimestamp = 0;
        long mPingTime = 0;
        long mPrevCheckCall = 0;

        static final int MAX_VAL = 256;
        static final int ADD_VALUE = 1;

        // @return: -1: race detected, 0..MAX_INT: sent checking value
        public short newCheck() {
            Log.e(TAG, "Check call interval: " + (System.currentTimeMillis() - mPrevCheckCall));
            mPrevCheckCall = System.currentTimeMillis();
//            if (mCheckIsPending) {
//                mRaceDetected = mRaceDetected < Short.MAX_VALUE ? mRaceDetected+1 : 1;
//                return -1;
//            }
            mLastSentValue++;
            if (mLastSentValue >= MAX_VAL) {
                mLastSentValue = 0;
            }
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
                mPingTime = (System.currentTimeMillis() - mSentTimestamp) / 2;
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
