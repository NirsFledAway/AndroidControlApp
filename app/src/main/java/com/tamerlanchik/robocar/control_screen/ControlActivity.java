package com.tamerlanchik.robocar.control_screen;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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

public class ControlActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";


//  Primary components
    private LogStorage mLogger;
    CommunicationHandler mCommunicationHandler;
    ControlsLivedataDispatcher mControlsDispatcher;

//  UI Views
    private SwitchCompat mConnectSwitch;
    private ImageView mPingStatusImageView;
    TextView mPingValueTextView;
    private List<Joystick> mJoystickViews = new ArrayList<>();
    private List<TextView> mJoystickValuesTextViews = new ArrayList<>();

// config
    String deviceAddress;
//    private SignalGraph mControlGraph, mErrGraph, mSumErrGraph, mGyroValueGraph;
//    private GraphView mGraphControl;
//    private LineGraphSeries<DataPoint> mControlSeries;

//    @Override
//    public void onReceive(MessageManager.Message msg) {
//        if (msg == null) {
//            return;
//        }
//        if (msg.cmd == null || msg.cmd == MessageManager.Command.VOID) {
//            mLogger.write(new String(msg.data));
//            return;
//        }
//        switch (msg.cmd) {
//            case TELEMETRY:
//                String payload = msg.stringData().trim();
//                String values[] = payload.split(" ");
//                if (values == null || values.length == 0) {
//                    break;
//                }
//                long U, err, sumErr;
//                double gyroZ;
//                try {
//                    U = Integer.parseInt(values[0]);
//                    err = Integer.parseInt(values[1]);
//                    sumErr = Integer.parseInt(values[2]);
//                    gyroZ = Double.parseDouble(values[3]);
//                } catch (NumberFormatException e) {
//                    mLogger.write("Cannot parse telemetry: " +values[0]);
//                    break;
//                }
//                long millis = System.currentTimeMillis();
//                mControlGraph.add(millis, U);
//                mErrGraph.add(millis, err);
//                mSumErrGraph.add(millis, sumErr);
//                mGyroValueGraph.add(millis, gyroZ);
//                break;
//            default:
//                mLogger.write(new String(msg.data));
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogger = new ViewModelProvider(this).get(LogStorage.class);
        mLogger.write("Hello!");

        deviceAddress = getResources().getString(R.string.dronAddress);

        mControlsDispatcher = new ViewModelProvider(this).get(ControlsLivedataDispatcher.class);
        mCommunicationHandler = new CommunicationHandler(this, deviceAddress);
        getLifecycle().addObserver(mCommunicationHandler);

        initUI();
        initBehaviour();

        Intent intent = getIntent();
        if (intent != null) {
            deviceAddress = intent.getStringExtra("device");
        } else {
            mLogger.write("No device selected");
        }
    }

    private void initUI() {
        setContentView(R.layout.activity_control_landscape);

        ViewPager2 mfi_1 = findViewById(R.id.top_mfi_view_pager);
        mfi_1.setAdapter(new ViewPagerFragmentAdapter(this));

        mJoystickViews.add(findViewById(R.id.joystick_1));
        mJoystickViews.add(findViewById(R.id.joystick_2));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_1_text_view));
        mJoystickValuesTextViews.add(findViewById(R.id.joystick_2_text_view));
        Joystick.OnJoystickChangeListener joystickChangeValueListener = new Joystick.OnJoystickChangeListener() {
            final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onStartTrackingTouch(Joystick g) {
                v.vibrate(VibrationEffect.createOneShot(10, 100));
            }
            @Override
            public void onValueChanged(Joystick g, Point value) {
            }
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onStopTrackingTouch(Joystick g) {
                v.vibrate(VibrationEffect.createOneShot(5, 100));
            }
        };
        for(int i = 0; i < 2; ++i) {
            mJoystickViews.get(i).setId(i);
            mJoystickViews.get(i).setOnJoystickChangeListener(joystickChangeValueListener);
        }

        mConnectSwitch = findViewById(R.id.connectSwitch);
        mPingStatusImageView = findViewById(R.id.ping_status_imageview);
        mPingValueTextView = findViewById(R.id.ping_value_textview);
    }

    private void initBehaviour() {
        // send joysticks values by timer
        TaskScheduler.get().addTask(TaskScheduler.TaskName.JOYSTICKS,
            getResources().getInteger(R.integer.send_joystick_period),
            ()-> runOnUiThread(()->{
                ArrayList<Point> jValues = new ArrayList<>(mJoystickViews.size());
                for(Joystick stick : mJoystickViews) {
                    Point value = stick.getValue();
                    if (Math.abs(value.x) < 40) {   // dead zone, to prevent accidentally movements
                        value.x = 0;
                    }
                    jValues.add(value);
                }
                mCommunicationHandler.sendJoysticks(jValues);
        }));

        // Update Stick TextViews
        int joysticksSendPeriod = getResources().getInteger(R.integer.send_joystick_period);
        TaskScheduler.get().addTask(TaskScheduler.TaskName.JOYSTICKS_UI_UPDATE, joysticksSendPeriod, ()-> runOnUiThread(()->{
            for(Joystick stick : mJoystickViews) {
                Point value = stick.getValue();
                String str = "X: " + value.x + " Y: " + value.y;
                mJoystickValuesTextViews.get(stick.mID).setText(str);
            }
        }));

        mConnectSwitch.setOnCheckedChangeListener((compoundButton, b) ->
                mControlsDispatcher.addData(ControlsLivedataDispatcher.CONNECT_KEY, Boolean.toString(b), true));
        mControlsDispatcher.getData(ControlsLivedataDispatcher.CONNECT_KEY, false).observe(this, (res) -> {
            if (Boolean.parseBoolean(res) != mConnectSwitch.isChecked()) {
                mConnectSwitch.toggle();
            }
        });

        mCommunicationHandler.getOnEventChan().observe(this, (msg)->{
            switch(msg.what) {
                case CommunicationHandler.CONNECTION_STATUS_CHANGED:
                    onConnectionStatusChanged((long) msg.obj);
                    break;
                default:
                    Log.e(TAG, "Bad CommunicationHandler answer: " + msg.what);
            }
        });
    }

    private void onConnectionStatusChanged(long ping) {
        int markId = R.drawable.log_mark_error;
        String pingValue = "0";
        if (ping > 0) {
            // connected
            markId = R.drawable.log_mark_incoming;
            pingValue = Long.toString(ping);
        }
        mPingStatusImageView.setImageDrawable(getResources().getDrawable(markId));
        mPingValueTextView.setText(pingValue);
    }
}
