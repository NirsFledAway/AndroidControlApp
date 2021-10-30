package com.tamerlanchik.robocar;

import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

public class MessageManager {
    public enum Mode {CHECK_CONN, MOTOR_COMM, SIGNAL_COMM, SENSOR_REQUEST, ALL};
    public static char[] mCode = {'#', '@'};
    public enum MessCode {EMPTY, CHKCONN, ANDR_CHK_CONN, CHASSIS_COMM, DEF1};

    public enum Command {VOID, SET_CONFIG, TELEMETRY, MOVEMENT_CMD, PING, SET_TARGET};

    public static final byte CommandCodes[] = new byte[]{'\0', 'K', 'O', 'M', 'L', 'N'};

    public static double mDiffK = 0.3;

    public static class Message {
        public Command cmd;
        public byte[] data;

        public String stringData() {
            return new String(data);
        }
    }

    public interface Listener {
        void onReceive(Message msg);
    }

    public MessageManager(Listener listener) {
        mListener = listener;
        mStorage = new Storage();
    }

    private Listener mListener;
    private Storage mStorage;
    public static byte[] buildJoystickMessage(Point data){
        List<Byte> l = new ArrayList<>();

        //l.add((byte)'#');
        l.add((byte)MessCode.CHASSIS_COMM.ordinal());
        copyArrayToList(l, intToByte(-data.y));
        copyArrayToList(l, intToByte(-data.x));
        l.add((byte)';');

        byte a[] = new byte[l.size()];
        for(int i=0; i<l.size(); i++){
            a[i] = l.get(i);
        }
        return a;
    }
    public static String buildJoystickTextMessage(Point data){
        data = preparePoint(data);
        String s = new String();
        s += "M|";
//        s += Float.toString(data.x);
        s += Integer.toString((int)data.x);
        s += " ";
        s += Integer.toString((int)data.y);
//        s += Float.toString(data.y);
        s += "$";
        return s;
    }

    public static Point preparePoint(Point src) {
        int basis = -src.y;
        double diff = src.x * mDiffK;
        Point p = new Point();
//        if (Math.abs(basis) < 50) {
//            p.x = src.x;
//            p.y = -src.x;
//            return p;
//        }
//        int dist = Math.min(255 - (int)(Math.abs(basis) + Math.abs(diff)), 0);
//        double diffX = diff/2 + dist;
//        double diffY = diff/2 - dist;
//
//        p.x = basis + (int)diffX;
//        p.y = basis - (int)diffY;

        p.x = -src.y;
        p.y = src.x;
        return p;
    }

    class Storage{
        public ArrayList<Byte> data;

        public Storage() {
            data = new ArrayList<>(1024);
        }

        public void append(byte dat) {
            data.add(dat);
        }

        public void append(byte[] dat, int start, int end) {
            if (end >= dat.length) {
                end = dat.length - 1;
            }
            if (end < 0) {
                return;
            }
            for(int i = start; i <= end; ++i) {
                data.add(dat[i]);
            }
        }
    }
    public void handleMessage(byte[] mess){
        if (mess.length == 0) {
            return;
        }

        for (byte b : mess) {
            if(b != '\n') {
                if (b != '$' && b != '\r') {
                    mStorage.append(b);
                }
                continue;
            }
            if (mStorage.data.size() == 0) {
                continue;
            }
            Message msg = new Message();

            for (int i = 0; i < CommandCodes.length; ++i) {
                if (mStorage.data.get(0) == CommandCodes[i]) {
                    msg.cmd = Command.values()[i];
                    break;
                }
            }

            int start = 2;
            if (msg.cmd == null || msg.cmd == Command.VOID) {
                start = 0;
            }
            msg.data = new byte[mStorage.data.size()];
            for(int i = start; i < mStorage.data.size(); ++i) {
                msg.data[i] = mStorage.data.get(i);
            }

            mListener.onReceive(msg);
            mStorage = new Storage();
        }

//        int start = 0;
//        while(true) {
//            if (start + 1 >= mess.length) {
//                return;
//            }
//            int idx = findInArray(mess, (byte) '\n', start, mess.length);
//            mStorage.append(mess, start, idx - 1);
//            if (idx == -1) {
//                return;
//            }
//
//            Message msg = new Message();
//            for (int i = 0; i < CommandCodes.length; ++i) {
//                if (mStorage.data.get(0) == CommandCodes[i]) {
//                    msg.cmd = Command.values()[i];
//                    break;
//                }
//            }
////            if (msg.cmd == null || msg.cmd == Command.VOID) {
////                mListener.onReceive(msg);
////                continue;
////            }
//            int end = mStorage.data.size()-1;
//            if (mStorage.data.get(mStorage.data.size()-1) == '$') {
//                end--;
//            }
//            if (mStorage.data.size() < 3) {
//                mStorage = new Storage();
//                continue;
//            }
//            msg.data = new byte[mStorage.data.size() - 1];
//            int i = 0;
//            for (Byte b : mStorage.data.subList(2, end+1)) {
//                msg.data[i++] = b;
//            }
//            //        msg.data = (byte[]) mStorage.data.subList(2, mStorage.data.size()).toArray();
//            mListener.onReceive(msg);
//            mStorage = new Storage();
//            start = idx + 1;
//        }
    }

    private static void copyArrayToList(List<Byte> l, byte[] d){
        for(byte b : d){
            l.add(b);
        }
    }

    private static byte[] floatToByte(float a){
        return ByteBuffer.allocate(Float.SIZE/8).putFloat(a).array();
    }

    private static byte[] intToByte(int a){
        return ByteBuffer.allocate(Integer.SIZE/8).putInt(a).array();
    }

    private static float byteToFloat(byte[] b){
        return ByteBuffer.wrap(b).getFloat();
    }

    private static int byteToInt(byte[] b){
        return ByteBuffer.wrap(b).getInt();
    }

    public static boolean isControlData(byte [] message){
        /*if(findInArray(code, message[0]) >= 0){
            return true;
        }else{
            return false;
        }*/
        return true;
    }

    private static int findInArray(byte [] a, byte c){
        return findInArray(a, c, 0, a.length-1);
    }

    private static int findInArray(byte [] a, byte c, int start, int end){
        if (end >= a.length) {
            end = a.length-1;
        }
        for(int i=start; i<=end; i++){
            if(a[i] == c){
                return i;
            }
        }
        return -1;
    }
}