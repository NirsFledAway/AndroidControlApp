package com.tamerlanchik.robocar.control_screen.log_subsystem;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class LogStorage extends AndroidViewModel {
    private static LogStorage mLogStorage;
    MutableLiveData<LogItem> mLastLog;
    List<LogItem> mLog;

    public LogStorage(@NonNull Application application) {
        super(application);
        mLog = new ArrayList<>();
        mLastLog = new MutableLiveData<>();
    }

    public void add(LogItem item){
        mLog.add(item);
    }
    public List<LogItem> getLog(){
        return mLog;
    }

    public LiveData<LogItem> getLiveData() {
        return mLastLog;
    }

    public void write(String message){
        write(new LogItem(message));
    }

    public void write(final LogItem message){
        mLog.add(message);
        mLastLog.postValue(message);
    }
}