package com.tamerlanchik.robocar;

import java.util.Date;

public class LogItem {
    private boolean mIsInternalMessage;
    private boolean mIsError;
    private String mMessage;
    private Date mTime;

    public LogItem(){
        mTime = new Date();
    }
    public LogItem(String message){
        mTime = new Date();
        mMessage = message;
        mIsInternalMessage = true;
        mIsError = false;
    }
    public LogItem(String message, boolean sender, boolean isError){
        mIsInternalMessage = sender;
        mIsError = isError;
        mTime = new Date();
        mMessage = message;
    }
    public LogItem(String message, boolean isError){
        mIsInternalMessage = true;
        mIsError = isError;
        mTime = new Date();
        mMessage = message;
    }

    public boolean isInternalMessage() {
        return mIsInternalMessage;
    }

    public void setInternalMessage(boolean internalMessage) {
        mIsInternalMessage = internalMessage;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        mMessage = message;
    }

    public Date getTime() {
        return mTime;
    }


    public boolean isError() {
        return mIsError;
    }

    public void setError(boolean error) {
        mIsError = error;
    }
}