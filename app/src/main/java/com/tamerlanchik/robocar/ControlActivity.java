package com.tamerlanchik.robocar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.jjoe64.graphview.GraphView;

import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.UICallback;
import com.tamerlanchik.robocar.transport.bluetooth.BluetoothController;
import com.tamerlanchik.robocar.transport.bluetooth.SerialListener;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ControlActivity extends AppCompatActivity implements UICallback, SerialListener, MessageManager.Listener {
    private static final String TAG = "Main Activity";
    private  static final String ACTION_USB_PERMISSION = "com.andrey.arduinousb.USB_PERMISSION";

    @Override
    public void handleStatus(Event event, String data) {
        LogItem logitem = new LogItem();
        if (event == Event.ERROR) {

        }

    }

    private enum Connected { False, Pending, True }

    private Logger mLogger;

    private Joystick g;
    private ToggleButton mConnectionButton;
    private Switch mConnectSwitch;
    private TextView mLogTextView;
    private ScrollView mLogScrollView;
    private CheckBox serialSpeedCheckBox;

    private TextView[] mGyroTextViews;
    private TextView[] mAccelTextViews;
    private EditText mDifferentialKTestView;
    private Button mResetButton;
    private TextView mJoystickValuesTextView;
    private TextView mCommandValuesTextView;
    private SignalGraph mControlGraph, mErrGraph, mSumErrGraph, mGyroValueGraph;
//    private GraphView mGraphControl;
//    private LineGraphSeries<DataPoint> mControlSeries;

    private String deviceAddress;

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
//        onReceive(new MessageManager.Message(){MessageManager.});
//        mLogger.write(new String(data));
//        return;
        mMessagemanager.handleMessage(data);

//        MessageManager.Message msg = MessageManager.handleMessage(data);
//        if (msg == null || msg.cmd == null || msg.cmd == MessageManager.Command.VOID) {
//            mLogger.write(new String(data));
//            return;
//        }
//        switch (msg.cmd) {
//            case TELEMETRY:
//                String payload = msg.stringData();
//                mAccelTextViews[1].setText(payload);
//                break;
//            default:
//                mLogger.write(new String(data));
//        }
//        if(hexEnabled) {
//            receiveText.append(TextUtil.toHexString(data) + '\n');
//        } else {
//            String msg = new String(data);
//            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
//                // don't show CR as ^M if directly before LF
//                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
//                // special handling if CR and LF come in separate fragments
//                if (pendingNewline && msg.charAt(0) == '\n') {
//                    Editable edt = receiveText.getEditableText();
//                    if (edt != null && edt.length() > 1)
//                        edt.replace(edt.length() - 2, edt.length(), "");
//                }
//                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
//            }
//            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
//        }
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
//                Random r = new Random();
//                U = r.nextInt(25);
                long millis = System.currentTimeMillis();
                mControlGraph.add(millis, U);
                mErrGraph.add(millis, err);
                mSumErrGraph.add(millis, sumErr);
                mGyroValueGraph.add(millis, gyroZ);
//                mControlSeries.appendData(new DataPoint((new Long(millis)).doubleValue(), U), true, 3000);
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        mControlSeries.appendData(new DataPoint(i++, r.nextInt(25)), true, 300);
//                    }
//                });
//                int i = 0;
//                for(String data : payload.split(" ")) {
//                    if (data == "" || data == " ") {
//                        continue;
//                    }
//                    try {
//                        int d = Integer.parseInt(data);
//                        if (i < 3) {
//                            mAccelTextViews[i].setText(data);
//                        } else {
//                            mGyroTextViews[i+2].setText(data);
//                        }
//                        ++i;
//                    } catch (NumberFormatException e) {
//                        Log.e("Robocar", e.toString());
//                        return;
//                    }
//                }
//                mAccelTextViews[1].setText(payload);
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
                sendMovement(g.getValue());
                setTimerTask();
            }
        };
        mSendControlTimer.schedule(task, 50);  // 50ms
    }

    class SignalGraph {
        private GraphView mGraph;
        private LineGraphSeries<DataPoint> mSeries;

        final int mTimeWindowWidth = 3;
        final int maxDataPoints = 3000;

        public SignalGraph(int viewId, String title){
            mGraph = (GraphView) findViewById(viewId);
            mSeries = new LineGraphSeries<>();
            mGraph.addSeries(mSeries);
            mGraph.getViewport().setXAxisBoundsManual(true);
            mGraph.getViewport().setMinX(-1*mTimeWindowWidth*1000);
            mGraph.getViewport().setMaxX(0);
            mGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);

            GridLabelRenderer gridLabel = mGraph.getGridLabelRenderer();
            gridLabel.setVerticalAxisTitle(title);
//            mGraph.setTitle(title);

            GridLabelRenderer glr = mGraph.getGridLabelRenderer();
            glr.setPadding(40); // should allow for 3 digits to fit on screen
            glr.setTextSize(20);
        }

        void add(long time, double value) {
            mSeries.appendData(new DataPoint((new Long(time)).doubleValue(), value), true, maxDataPoints);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        mLogger = new Logger(ControlActivity.this);
        mLogger.write("Hello!");

        Intent intent = getIntent();
        if (intent != null) {
            deviceAddress = intent.getStringExtra("device");
        } else {
            mLogger.write("No device selected");
        }

        mSerial = new BluetoothController(this, deviceAddress);

        mSendTimeout = System.currentTimeMillis();

        g = (Joystick)findViewById(R.id.graph);
        g.setOnJoystickChangeListener(new Joystick.OnJoystickChangeListener() {
            @Override
            public void onStartTrackingTouch(Joystick g) {
                Log.d(TAG, " Joystick Start Tracking");
            }

            @Override
            public void onValueChanged(Joystick g, Point value) {
                setMovementValue(mJoystickValuesTextView, value);
//                sendMovement(value);
//                if (Math.abs(value.x) < 50) {
//                    value.x = 0;
//                }
//                if (Math.abs(value.y) < 50) {
//                    value.y = 0;
//                }
//                if ((new Date()).getTime() - lastSent.getTime() < 50 ) {
//                    return;
//                }
//                value.x = (int)(value.x * 1.4);
//                value.y = (int)(value.y * 1.4);
//                lastSent = new Date();
////                value.x = (int)Math.signum(value.x)*(Math.abs(value.x) + 80);
////                value.y = (int)Math.signum(value.y)*(Math.abs(value.y) + 80);
//                mLogger.write("Values - X: " + Integer.toString(value.x) + " Y: " + Integer.toString(value.y));
//                send(MessageManager.buildJoystickTextMessage(value));
////                if(mSerial.write(MessageManager.buildJoystickMessage(value))){
////                    mLogger.write("Sent");
////                }else{
////                    mLogger.write(new LogItem("Error sending", true));
////                }
//                mSendTimeout = System.currentTimeMillis();
            }

            @Override
            public void onStopTrackingTouch(Joystick g) {
                Log.d(TAG, " Joystick Stop Tracking");
                onValueChanged(g, new Point(0,0));
            }
        });

        mConnectionButton = (ToggleButton) findViewById(R.id.carConnectionStateButten);

        mConnectSwitch = (Switch) findViewById(R.id.connectSwitch);
        mConnectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    mLogger.write("Trying to reconnect...");
                    mSerial.onResume();
                } else {
                    mLogger.write("Disconnecting...");
                    mSerial.onPause();
                }
            }
        });

//        mGyroTextViews = new TextView[]{
//            (TextView)findViewById(R.id.gyroXTextView),
//            (TextView)findViewById(R.id.gyroYTextView),
//            (TextView)findViewById(R.id.gyroZTextView),
//        };
//        mAccelTextViews = new TextView[]{
//            (TextView)findViewById(R.id.accelXTextView),
//            (TextView)findViewById(R.id.accelYTextView),
//            (TextView)findViewById(R.id.accelZTextView),
//        };

        mMessagemanager = new MessageManager(this);

        mDifferentialKTestView = (EditText) findViewById(R.id.rotationK);
        mDifferentialKTestView.setText(Double.toString(MessageManager.mDiffK));
        mDifferentialKTestView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                MessageManager.mDiffK = Double.parseDouble(v.getText().toString());
//                if (event.getAction() == KeyEvent.ACTION_DOWN) {
//                    MessageManager.mDiffK = Integer.parseInt(v.getText().toString());
//                }
                return false;
            }
        });

//        serialSpeedCheckBox = (CheckBox)findViewById(R.id.speedCheckBox);
//
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(ACTION_USB_PERMISSION);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
//        registerReceiver(mSerial.getSerialBroadCastReceiver(), filter);
        mCarConfig = new Config();
        mCarConfig.onCreate(this);

        mResetButton = (Button) findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cmd = "R|0";
                send(cmd);
            }
        });

        mJoystickValuesTextView = (TextView) findViewById(R.id.joystickValuesTextView);
        setMovementValue(mJoystickValuesTextView, new Point(0,0));
        mCommandValuesTextView = (TextView) findViewById(R.id.commandValuesTextView);
        setMovementValue(mCommandValuesTextView, new Point(0,0));

        mControlGraph = new SignalGraph(R.id.graphControl, "U");
        mErrGraph = new SignalGraph(R.id.graphErr, "E");
        mSumErrGraph = new SignalGraph(R.id.graphSumErr, "sum E");
        mGyroValueGraph = new SignalGraph(R.id.graphGyroZ, "Gyro Z");
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
            runOnUiThread(() -> setMovementValue(mCommandValuesTextView, MessageManager.preparePoint(value)));
        }
    }

    @Override
    public void onDestroy() {
        mLogger.write("onDestroy");
        mConnectSwitch.setChecked(falseпше );
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
