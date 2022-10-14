package com.tamerlanchik.robocar.transport.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.tamerlanchik.robocar.control_screen.log_subsystem.Logger;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class SerialModule {
    public static final String ACTION_USB_PERMISSION = "com.andrey.arduinousb.USB_PERMISSION";
    private static final String TAG = "SerialModule";

    private Context mContext;

    private UsbManager mUSBManager;
    private UsbDevice mUsbDevice;
    private UsbSerialDevice mSerialPort;
    private UsbDeviceConnection mConnection;
    private int mSerialSpeed=9600;

    private ViewBound mVB = null;
    private UsbSerialInterface.UsbReadCallback mCallback;
    private Logger mLogger;

    public interface ViewBound{
        void usbDetached();
        void usbAttached();
        void messageReceived(byte[] message);
    }

    private final BroadcastReceiver mSerialBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(ACTION_USB_PERMISSION)) {
                if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)){
                    if(mSerialPort.open()) {
                        mSerialPort.close();
                    }
                    mLogger.write("An Arduino board detached!");
                    mVB.usbDetached();
                }else if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){
                    mLogger.write("A USB device attached!");
                    mVB.usbAttached();
                }
                return;
            }

            //Making a Serial connection
            if (!intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)) {
                mLogger.write("No permission granted");
                return;
            }
            mConnection = mUSBManager.openDevice(mUsbDevice);
            mSerialPort = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mConnection);
            if (mSerialPort == null) {
                mLogger.write("Serial port is null");
                return;
            }
            if (!mSerialPort.open()) {
                mLogger.write("Serial port is not open!");
                return;
            }
            mSerialPort.setBaudRate(mSerialSpeed);
            mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
            mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
            mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
            mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            mSerialPort.read(mCallback);

            mLogger.write("Serial Connection is Opened!");
        }
    };


    public SerialModule(Context context, Logger logger){
        mContext = context;
        mCallback = new UsbSerialInterface.UsbReadCallback() {
            @Override
            public void onReceivedData(byte[] arg0) {
                try {
                    String data = new String(arg0, "UTF-8");
                    mVB.messageReceived(arg0);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        };
        mLogger = logger;
    }

    public boolean connectSerial(int speed){
        mSerialSpeed = speed;
        HashMap<String, UsbDevice> usbDevices = mUSBManager.getDeviceList();
        mLogger.write("Found " + Integer.toString(usbDevices.size()) + " USB devices");
        if(!usbDevices.isEmpty()){
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                mUsbDevice = entry.getValue();
                int deviceVID = mUsbDevice.getVendorId();
                mLogger.write("Device #" + Integer.toString(deviceVID) + "\n");
                if (deviceVID == 0x2341 || deviceVID == 6790) {
                    mLogger.write("An Arduino board found!");
                    PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    mUSBManager.requestPermission(mUsbDevice, pi);
                    return true;
                }
            }
            mLogger.write("Scanning completed");
        }else{
            Toast.makeText(mContext, "No USB Devices Found!", Toast.LENGTH_SHORT);
        }
        return false;
    }

    public void disconnect(){
        if(mSerialPort.open()) {
            mSerialPort.close();
            Log.d(TAG, "Serial closed");
            mLogger.write("Disconnected from Arduino");
        }

    }

    public void setUSBManager(UsbManager usbManager){
        mUSBManager = usbManager;
    }

    public void setVB(ViewBound tempVB){
        mVB = tempVB;
    }

    public void setSpeed(int sp){
        mSerialSpeed = sp;
    }

    public BroadcastReceiver getSerialBroadCastReceiver() {
        return mSerialBroadCastReceiver;
    }

    public boolean write(String message){
        return write(message.getBytes());
    }

    public boolean write(byte mess[]){
        if(mSerialPort!=null) {
            mSerialPort.write(mess);
            return true;
        }
        return false;
    }
}
