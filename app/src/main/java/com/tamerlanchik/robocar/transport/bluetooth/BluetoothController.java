package com.tamerlanchik.robocar.transport.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tamerlanchik.robocar.transport.Communicator;
import com.tamerlanchik.robocar.transport.Event;
import com.tamerlanchik.robocar.transport.Package;
import com.tamerlanchik.robocar.transport.UICallback;

import java.io.IOException;

public class BluetoothController implements Communicator, ServiceConnection, SerialListener {
    private enum Connected { False, Pending, True }


    private Activity mContext;
    private SerialService mService;
    private boolean initialStart = true;
    private Connected isConnected = Connected.False;
    private String mDeviceAddress;

    MutableLiveData<Event> mEventLiveData = new MutableLiveData<>();

    private void writeLog(UICallback.Event event, String message) {
        ((UICallback)mContext).handleStatus(event, message);
    }

    public BluetoothController(Activity context, String deviceAddress) {
        mContext = context;
        mDeviceAddress = deviceAddress;
        context.bindService(new Intent(context, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        if(mService != null) {
            mService.attach(this);
        } else {
            mContext.startService(new Intent(mContext, SerialService.class));
        }
    }

    @Override
    public void onStop() {
        if(mService != null && !mContext.isChangingConfigurations())
            mService.detach();
    }

    @Override
    public void onDestroy() {
        onPause();
        mContext.stopService(new Intent(mContext, SerialService.class));
    }

    @Override
    public void onResume() {
        if((initialStart || isConnected == Connected.False) && mService != null) {
            initialStart = false;
//            connect();
            mContext.runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        if (isConnected != Connected.False)
            disconnect();
    }

    private boolean connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mDeviceAddress);
//            status("connecting...");
//            mLogger.write("Connecting...");
            isConnected = Connected.Pending;
            SerialSocket socket = new SerialSocket(mContext.getApplicationContext(), device);
            mService.connect(socket);
            isConnected = Connected.True;
            return true;
        } catch (Exception e) {
            onSerialConnectError(e);
            isConnected = Connected.False;
            return false;
        }
    }

    public boolean disconnect() {
        isConnected = Connected.False;
        mService.disconnect();
        return true;
    }

    @Override
    public boolean send(Package message) {
        try {
            mService.write(message.getBinary());
        } catch (IOException e) {
            ((UICallback)mContext).handleStatus(UICallback.Event.ERROR, "Cannot send to serial: " + e.toString());
        }
        return false;
    }

    @Override
    public LiveData<Event> getOnEventChan() {
        return mEventLiveData;
    }

    @Override
    public boolean isConnected() {
        return isConnected == Connected.True;
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        writeLog(UICallback.Event.Note, "onServiceConnected. connecting...");
        mService = ((SerialService.SerialBinder) binder).getService();
        mService.attach(this);
        if(initialStart) {
            initialStart = false;
//            connect();
            mContext.runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        writeLog(UICallback.Event.Note, "onServiceDisconected");
    }

    @Override
    public void onSerialConnect(boolean connected) {
        if (connected) {
            isConnected = Connected.True;
        } else {
            isConnected = Connected.False;
        }
        mEventLiveData.postValue(new Event(Event.EventType.CONNECTED));
        ((SerialListener)mContext).onSerialConnect(connected);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        mEventLiveData.postValue(new Event(e));
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        mEventLiveData.postValue(new Event(new Package(data)));
    }

    @Override
    public void onSerialIoError(Exception e) {

    }
}
