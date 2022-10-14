package com.tamerlanchik.robocar.control_screen;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.tamerlanchik.robocar.R;
import com.tamerlanchik.robocar.TextUtil;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
//import com.tamerlanchik.robocar.control_screen.log_subsystem.ViewPagerFragmentAdapter;
import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.UICallback;
import com.tamerlanchik.robocar.transport.bluetooth.SerialListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ControlActivity extends AppCompatActivity implements UICallback, SerialListener, MessageManager.Listener {
    private static final String TAG = "Main Activity";
    private  static final String ACTION_USB_PERMISSION = "com.andrey.arduinousb.USB_PERMISSION";
    private String deviceAddress;

    @Override
    public void handleStatus(Event event, String data) {
        LogItem logitem = new LogItem();
        if (event == Event.ERROR) {

        }

    }

    private enum Connected { False, Pending, True }

    private LogStorage mLogger;

//  UI Views
    private ToggleButton mConnectionButton;
    private Switch mConnectSwitch;
    private List<Joystick> mJoystickViews = new ArrayList<>();
    private List<TextView> mJoystickValuesTextViews = new ArrayList<>();

    private Button mResetButton;
    private TextView mJoystickValuesTextView;
    private SignalGraph mControlGraph, mErrGraph, mSumErrGraph, mGyroValueGraph;
//    private GraphView mGraphControl;
//    private LineGraphSeries<DataPoint> mControlSeries;


    private Communicator mSerial;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private Timer mSendControlTimer;

    private Config mCarConfig;

    class Config {
        EditText mKiEditText;
        EditText mKpEditText;
        EditText mKdEditText;
        EditText mErrAddEditText;
        EditText mTimeFactorEditText;
        EditText mAngluarSpeedFactor;

        final String KpKey = "ctrl.Kp";
        final String KiKey = "ctrl.Ki";
        final String KdKey = "ctrl.Kd";
        final String ErrAddKey = "ctrl.errAddFactor";
        final String TimeIntegrDivider = "ctrl.timeIntegrDivider";
        final String AngularSpeedFactorKey = "ctrl.AngSpFct";

        AppCompatActivity mActivity;

        SharedPreferences mSharedPreferences;

        boolean mRadioState = false;

        public void onCreate(AppCompatActivity activity) {
            mActivity = activity;

            mSharedPreferences = mActivity.getSharedPreferences("carConfig", MODE_PRIVATE);

            mKpEditText = (EditText) mActivity.findViewById(R.id.KpEditText);
            mKpEditText.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(KpKey, v.getText().toString());
                return false;
            });

            mKiEditText = (EditText) mActivity.findViewById(R.id.KiEditText);
            mKiEditText.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(KiKey, v.getText().toString());
                return false;
            });

            mKdEditText = (EditText) mActivity.findViewById(R.id.KdEditText);
            mKdEditText.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(KdKey, v.getText().toString());
                return false;
            });

            mErrAddEditText = (EditText) mActivity.findViewById(R.id.errAddEditText);
            mErrAddEditText.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(ErrAddKey, v.getText().toString());
                return false;
            });

            mTimeFactorEditText = (EditText) mActivity.findViewById(R.id.timeFactorEditText);
            mTimeFactorEditText.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(TimeIntegrDivider, v.getText().toString());
                return false;
            });

            mAngluarSpeedFactor = (EditText) mActivity.findViewById(R.id.angularSpeedFactor);
            mAngluarSpeedFactor.setOnEditorActionListener((v, actionId, event) -> {
                onValueChange(AngularSpeedFactorKey, v.getText().toString());
                return false;
            });

            flushAll();
        }

        public void flushAll() {
            onValueChange(mKpEditText, KpKey, mSharedPreferences.getString(KpKey, "0"));
            onValueChange(mKiEditText, KiKey, mSharedPreferences.getString(KiKey, "0"));
            onValueChange(mKdEditText, KdKey, mSharedPreferences.getString(KdKey, "0"));
            onValueChange(mErrAddEditText, ErrAddKey, mSharedPreferences.getString(ErrAddKey, "0"));
            onValueChange(mTimeFactorEditText, TimeIntegrDivider, mSharedPreferences.getString(TimeIntegrDivider, "0"));
            onValueChange(mAngluarSpeedFactor, AngularSpeedFactorKey, mSharedPreferences.getString(AngularSpeedFactorKey, "0"));
        }

        private void onValueChange(String name, String value) {
            String cmd = "K|" + name + " " + value;
            if (mRadioState) {
                send(cmd);
                mLogger.write("Sent config: " + name + ": " + value);
            }
            mSharedPreferences.edit().putString(name, value).apply();
            mLogger.write("Change config: " + name + ": " + value);
        }

        private void onValueChange(TextView tw, String name, String value) {
            onValueChange(name, value);
            tw.setText(value);
        }

        public void onCommunicatorStateChanged(boolean status) {
            mRadioState = status;
            if (status) {
                flushAll();
            }
        }
    }

    private MessageManager mMessagemanager;
    Date lastSent = new Date();

    private long mSendTimeout;

    @Override
    public void onSerialConnect(boolean connected) {
        if (connected) {
            mLogger.write("Serial connected");
            mSendControlTimer = new Timer();
            setTimerTask();
        } else {
            mSendControlTimer.cancel();
            mSendControlTimer = null;
            mLogger.write("Serial disconnected");
        }
        mConnectSwitch.setChecked(connected);
        mCarConfig.onCommunicatorStateChanged(connected);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mLogger.write("Connecton failed: " + e.getMessage());
        Log.e(TAG, "Connecton failed: " + e.getMessage());
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        mLogger.write("Connecion lost: "+ e.getMessage());
        Log.e(TAG, "Connecion lost: "+ e.getMessage());
    }

    private boolean send(String str) {
        if (!mSerial.isConnected()) {
//        if(connected != Connected.True) {
            mLogger.write(new LogItem("Not connected", true));
//            Toast.makeText(ControlActivity.this, "not connected", Toast.LENGTH_SHORT).show();
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
            SpannableStringBuilder spn = new SpannableStringBuilder(msg+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
//            mLogger.write("Gonna send: " + spn.toString());
            mSerial.send(data);
//            service.write(data);
//            mLogger.write(spn.toString());
        } catch (Exception e) {
            onSerialIoError(e);
            return false;
        }
        return true;
    }

    private void receive(byte[] data) {
        mMessagemanager.handleMessage(data);
    }

    @Override
    public void onReceive(MessageManager.Message msg) {
        if (msg == null) {
            return;
        }
        if (msg.cmd == null || msg.cmd == MessageManager.Command.VOID) {
            mLogger.write(new String(msg.data));
            return;
        }
        switch (msg.cmd) {
            case TELEMETRY:
                String payload = msg.stringData().trim();
                String values[] = payload.split(" ");
                if (values == null || values.length == 0) {
                    break;
                }
                long U, err, sumErr;
                double gyroZ;
                try {
                    U = Integer.parseInt(values[0]);
                    err = Integer.parseInt(values[1]);
                    sumErr = Integer.parseInt(values[2]);
                    gyroZ = Double.parseDouble(values[3]);
                } catch (NumberFormatException e) {
                    mLogger.write("Cannot parse telemetry: " +values[0]);
                    break;
                }
                long millis = System.currentTimeMillis();
                mControlGraph.add(millis, U);
                mErrGraph.add(millis, err);
                mSumErrGraph.add(millis, sumErr);
                mGyroValueGraph.add(millis, gyroZ);
                break;
            default:
                mLogger.write(new String(msg.data));
        }
    }

    private void setTimerTask() {
        if (mSendControlTimer == null) {
            return;
        }
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                sendMovement(mJoystickViews.get(0).getValue());
                setTimerTask();
            }
        };
        mSendControlTimer.schedule(task, 50);  // 50ms
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_landscape);

        mLogger = new ViewModelProvider(this).get(LogStorage.class);
        mLogger.write("Hello!");

        ViewPager2 mfi_1 = findViewById(R.id.top_mfi_view_pager);
        mfi_1.setAdapter(new ViewPagerFragmentAdapter(this));

        Intent intent = getIntent();
        if (intent != null) {
            deviceAddress = intent.getStringExtra("device");
        } else {
            mLogger.write("No device selected");
        }

//        mSerial = new BluetoothController(this, deviceAddress);
//
//        mSendTimeout = System.currentTimeMillis();
//
        Joystick.OnJoystickChangeListener joystickChangeValueListener = new Joystick.OnJoystickChangeListener() {
            @Override
            public void onStartTrackingTouch(Joystick g) {
                Log.d(TAG, " Joystick Start Tracking");
            }
            @Override
            public void onValueChanged(Joystick g, Point value) {
//                TODO: обновление значений джойстика на textView по таймеру
                setMovementValue(mJoystickValuesTextViews.get(g.mID), value);
            }
            @Override
            public void onStopTrackingTouch(Joystick g) {
                setMovementValue(mJoystickValuesTextViews.get(g.mID), new Point(0,0));
            }
        };
        mJoystickViews.add(findViewById(R.id.joystick_1));
        mJoystickViews.add(findViewById(R.id.joystick_2));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_1_text_view));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_2_text_view));
        for(int i = 0; i < 2; ++i) {
            mJoystickViews.get(i).setId(i);
            mJoystickViews.get(i).setOnJoystickChangeListener(joystickChangeValueListener);
            setMovementValue(mJoystickValuesTextViews.get(i), new Point(0,0));
        }
        mConnectSwitch = findViewById(R.id.connectSwitch);
    }

    private void setMovementValue(TextView view, Point point) {
        String str = "X: " + point.x + " Y: " + point.y;
        view.setText(str);
    }

    private void sendMovement(Point value) {
        if (Math.abs(value.x) < 40) {
            value.x = 0;
        }
        value.y = (int)(value.y * 1.4);
        if (send(MessageManager.buildJoystickTextMessage(value))) {
//            runOnUiThread(() -> setMovementValue(mCommandValuesTextView, MessageManager.preparePoint(value)));
        }
    }

    @Override
    public void onDestroy() {
        mLogger.write("onDestroy");
        mConnectSwitch.setChecked(false);
        mSerial.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        mLogger.write("onStart");
        super.onStart();
        mConnectSwitch.setChecked(true);
    }

    @Override
    public void onStop() {
        mLogger.write("onStop");
//        mConnectSwitch.setChecked(false);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mLogger.write("onResume");
//        mConnectSwitch.setChecked(true);
    }


}
