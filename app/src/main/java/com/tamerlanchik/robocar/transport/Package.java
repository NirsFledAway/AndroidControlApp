package com.tamerlanchik.robocar.transport;

public class Package {
    byte[] mBinary;
    String mKey;

    public Package() {}
    public Package(String data) {
        setString(data);
    }
    public Package(byte[] data) {
        setBinary(data);
    }

    public String getString() {
        return mBinary.toString();
    }
    public void setString(String data) {
        mBinary = data.getBytes();
    }

    public byte[] getBinary() {
        return mBinary;
    }
    public void setBinary(byte[] bin) {
        mBinary = bin;
    }

    public String getKey() {
        return mKey;
    }

    public void setKey(String key) {
        this.mKey = mKey;
    }

    public Package setKey(int key) {
        this.mKey = Integer.toString(key);
        return this;
    }
}
