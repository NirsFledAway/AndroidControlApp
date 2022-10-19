package com.tamerlanchik.robocar.control_screen;

import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.tamerlanchik.robocar.CommunicationHandler;
import com.tamerlanchik.robocar.R;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
//import com.tamerlanchik.robocar.control_screen.log_subsystem.ViewPagerFragmentAdapter;
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

    private LogStorage mLogger;

//  UI Views
    private ToggleButton mConnectionButton;
    private Switch mConnectSwitch;
    private ImageView mPingStatusImageView;
    private List<Joystick> mJoystickViews = new ArrayList<>();
    private List<TextView> mJoystickValuesTextViews = new ArrayList<>();

    private Button mResetButton;
    private TextView mJoystickValuesTextView;
    private SignalGraph mControlGraph, mErrGraph, mSumErrGraph, mGyroValueGraph;
//    private GraphView mGraphControl;
//    private LineGraphSeries<DataPoint> mControlSeries;

    CommunicationHandler mCommunicationHandler;
    ControlsLivedataDispatcher mControlsDispatcher;

    private Timer mSendControlTimer;
    private TaskScheduler mScheduler = new TaskScheduler();

//    keys
    final String CONNECT_KEY = "connect_switch";

    Date lastSent = new Date();

    private long mSendTimeout;

    @Override
    public void onSerialConnect(boolean connected) {
        mCommunicationHandler.onSerialConnect(connected);
        if (connected) {
            mSendControlTimer = new Timer();
            setTimerTask();
        } else {
            mSendControlTimer.cancel();
            mSendControlTimer = null;
        }
        mConnectSwitch.setChecked(connected);
//        mCarConfig.onCommunicatorStateChanged(connected);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mCommunicationHandler.onSerialConnectError(e);
    }

    @Override
    public void onSerialRead(byte[] data) {
        mCommunicationHandler.receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        mCommunicationHandler.onSerialIoError(e);
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

        mControlsDispatcher = new ViewModelProvider(this).get(ControlsLivedataDispatcher.class);

//        mSerial = new BluetoothController(this, deviceAddress);
//
        mSendTimeout = System.currentTimeMillis();
//
        ViewPager2 mfi_1 = findViewById(R.id.top_mfi_view_pager);
        mfi_1.setAdapter(new ViewPagerFragmentAdapter(this));

        Joystick.OnJoystickChangeListener joystickChangeValueListener = new Joystick.OnJoystickChangeListener() {
            @Override
            public void onStartTrackingTouch(Joystick g) {
                Log.d(TAG, " Joystick Start Tracking");
            }
            @Override
            public void onValueChanged(Joystick g, Point value) {
//                TODO: обновление значений джойстика на textView по таймеру
//                setMovementValue(mJoystickValuesTextViews.get(g.mID), value);
            }
            @Override
            public void onStopTrackingTouch(Joystick g) {
//                setMovementValue(mJoystickValuesTextViews.get(g.mID), new Point(0,0));
            }
        };
        mJoystickViews.add(findViewById(R.id.joystick_1));
        mJoystickViews.add(findViewById(R.id.joystick_2));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_1_text_view));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_2_text_view));
        for(int i = 0; i < 2; ++i) {
            mJoystickViews.get(i).setId(i);
//            mJoystickViews.get(i).setOnJoystickChangeListener(joystickChangeValueListener);
            setMovementValue(mJoystickValuesTextViews.get(i), new Point(0,0));
        }
        mScheduler.addTask(TaskScheduler.TaskName.JOYSTICKS, 5000, ()->{
            runOnUiThread(()->{
                ArrayList<Point> jValues = new ArrayList<>(mJoystickViews.size());
                for(int i = 0; i < mJoystickViews.size(); ++i) {
                    Joystick view = mJoystickViews.get(i);
                    Point value = view.getValue();
                    jValues.add(i, value);
                    setMovementValue(mJoystickValuesTextViews.get(view.mID), value);
                }
                String message = "J1: " + jValues.get(0).toString() + "; J2: " + jValues.get(1).toString();
                mLogger.write(message);
                mCommunicationHandler.sendJoysticks(jValues);
            });
        });

        mConnectSwitch = findViewById(R.id.connectSwitch);
        mConnectSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            mControlsDispatcher.addData(ControlsLivedataDispatcher.CONNECT_KEY, Boolean.toString(b), true);
        });
        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, false).observe(this, (res) -> {
            if (Boolean.valueOf(res) != mConnectSwitch.isChecked()) {
                mConnectSwitch.toggle();
            }
        });

        mPingStatusImageView = findViewById(R.id.ping_status_imageview);


        Intent intent = getIntent();
        if (intent != null) {
            deviceAddress = intent.getStringExtra("device");
        } else {
            mLogger.write("No device selected");
        }

        mCommunicationHandler = new CommunicationHandler(this, deviceAddress);

        // Ping status checking
        mScheduler.addTask(TaskScheduler.TaskName.PING, 1000, ()->{
            LiveData<String> res = mControlsDispatcher.addData(ControlsLivedataDispatcher.PING_CMD, "", true);
            Timer t = new Timer();
            Observer<String> o = (item) -> {
                t.cancel();
                mPingStatusImageView.setImageDrawable(getResources().getDrawable(R.drawable.log_mark_incoming));
            };
            t.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(()-> {
                        res.removeObserver(o);
                        mPingStatusImageView.setImageDrawable(getResources().getDrawable(R.drawable.log_mark_error));
                    });
                }
            }, 900);

            runOnUiThread(()->{
                res.observe(ControlActivity.this, o);
            });
        });
    }

    void handleJoysticksValues() {

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
        if (mCommunicationHandler.send(MessageManager.buildJoystickTextMessage(value))) {
//            runOnUiThread(() -> setMovementValue(mCommandValuesTextView, MessageManager.preparePoint(value)));
        }
    }

    @Override
    public void onDestroy() {
        mLogger.write("onDestroy");
        mConnectSwitch.setChecked(false);
        mCommunicationHandler.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        mLogger.write("onStart");
        super.onStart();
        mCommunicationHandler.onStart();
        mConnectSwitch.setChecked(true);
    }

    @Override
    public void onStop() {
        mLogger.write("onStop");
        mCommunicationHandler.onStop();
//        mConnectSwitch.setChecked(false);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
//        communicationHandler.onResume();
        mLogger.write("onResume");
//        mConnectSwitch.setChecked(true);
    }



}
