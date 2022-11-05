package com.tamerlanchik.robocar.transport.binary_tools;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteStuffingPackager {
    static final String TAG = "ByteStuffingPackager";
    static final byte borderByte = 0x7E;
    static final byte escapeByte = 0x7D;
    static final byte escBorderByte = 0x5E;
    static final byte escEscapeByte = 0x5D;

    ByteBuffer mUnpackBuffer = ByteBuffer.allocate(1024);
    byte[] mTempBytes = null;
    boolean isCollectingNow = false;

    public static ByteBuffer packWithByteStuffing(ByteBuffer src) {

        int len = src.limit();
        ByteBuffer dest = ByteBuffer.allocate(len+2);
        dest.put(borderByte);
        for (int i = 0; i < len; ++i) {
            byte srcByte = src.get(i);
            switch (srcByte) {
                case borderByte:
                    dest.put(escapeByte);
                    dest.put(escBorderByte);
                    break;
                case escapeByte:
                    dest.put(escapeByte);
                    dest.put(escEscapeByte);
                    break;
                default:
                    dest.put(srcByte);
            }
        }

        dest.put(borderByte);
        return dest;
    }

    public List<ByteBuffer> unpackWithByteStuffing(ByteBuffer src) {
        List<ByteBuffer> res = new ArrayList<>();
        for(int i = 0; i < src.limit(); ++i) {
            byte currentByte = src.get(i);
            if (currentByte == borderByte) {
                if (!this.isCollectingNow) {
                    // start collect new frame
                    this.isCollectingNow = true;
                    mUnpackBuffer.position(0);
//                    mUnpackBuffer = ByteBuffer.allocate(1024);
                } else { // collecting now
                    if (mUnpackBuffer.position() == 0) {
                        // it was an end of prev frame, false-start
                        isCollectingNow = true;
                    } else {
                        // end collecting current frame
                        isCollectingNow = false;
                        res.add(mUnpackBuffer.duplicate());
                    }
                }
            } else {
                if (!isCollectingNow) {
                    // rubbish bytes, awaiting new frame begin
                    break;
                }
                if (currentByte == escapeByte) {
                    mTempBytes = new byte[1];
                    mTempBytes[0] = escapeByte;
                } else if (mTempBytes != null && mTempBytes[0] == escapeByte) {
                    switch (currentByte) {
                        case escBorderByte:
                            mUnpackBuffer.put(borderByte);
                            break;
                        case escEscapeByte:
                            mUnpackBuffer.put(escapeByte);
                            break;
                        default:
                            Log.e(TAG, "Bad escaping sequence: " + String.valueOf(currentByte));
                            continue;
                    }
                    mTempBytes = null;
                } else {
                    mUnpackBuffer.put(currentByte);
                }
            }
        }
        if (res.size() > 0) {
            return res;
        }
        return null;
    }
}
