package com.tamerlanchik.robocar;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class LogStorage{
    private static LogStorage mLogStorage;
    private List<LogItem> mLog;

    private LogStorage(Context context){
        mLog = new ArrayList<>();
    }
    public static LogStorage get(Context context){
        if(mLogStorage == null){
            mLogStorage = new LogStorage(context);
        }
        return mLogStorage;
    }
    public void add(LogItem item){
        mLog.add(item);
    }
    public List getLog(){
        return mLog;
    }
}