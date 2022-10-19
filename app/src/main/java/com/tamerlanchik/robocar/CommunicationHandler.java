package com.tamerlanchik.robocar;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.tamerlanchik.robocar.control_screen.ControlsLivedataDispatcher;
import com.tamerlanchik.robocar.control_screen.MessageManager;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.MessageBuilderV1;
import com.tamerlanchik.robocar.transport.Package;
import com.tamerlanchik.robocar.transport.bluetooth.BluetoothController;
import com.tamerlanchik.robocar.transport.bluetooth.SerialListener;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CommunicationHandler implements SerialListener {
    static final String TAG = "CommunicationHandler";
    Context mContext;
    LogStorage mLogger;
    MessageManager mMessagemanager;
    ControlsLivedataDispatcher mControlsDispatcher;

    Communicator mCommunicator;
    Connected connected = Connected.False;
    boolean initialStart = true;
    boolean hexEnabled = false;
    boolean pendingNewline = false;
    String newline = TextUtil.newline_crlf;

    public enum Connected { False, Pending, True }

//    cmd labels. From 0x00 to 0xff (1 byte)
    private int PING_LABEL = 0x1;
    private int JOYSTICKS_LABEL = 0x10;

    public CommunicationHandler(AppCompatActivity context, String deviceAddress) {
        mContext = context;
        mCommunicator = new BluetoothController(context, deviceAddress);
        mLogger = new ViewModelProvider(context).get(LogStorage.class);

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

        mControlsDispatcher.getData(ControlsLivedataDispatcher.PING_CMD, true).observe(context, (value)->{
            ping();
        });
    }

    public void ping() {
        String data = Integer.toString((int)(Math.random() * 100 + 1));
        try {
            send(MessageBuilderV1.build(PING_LABEL, data));
        } catch (Exception e)
        {
            Log.e(TAG, "Bad ping label");
        }
    }

    public void send(Package pkg) {
        mCommunicator.send(pkg);
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
        ByteBuffer buffer = ByteBuffer.allocate(4*4);
        for (int i = 0; i < 2; ++i) {
            Point p = values.get(i);
            buffer.putInt(p.x, p.y);
        }
        try {
            send(MessageBuilderV1.build(JOYSTICKS_LABEL, buffer.array()));
        } catch (Exception e)
        {
            Log.e(TAG, "Bad joystick label");
        }
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
}
