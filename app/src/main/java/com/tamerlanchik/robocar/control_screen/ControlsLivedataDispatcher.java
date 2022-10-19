package com.tamerlanchik.robocar.control_screen;

import android.app.Application;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.Map;

public class ControlsLivedataDispatcher extends AndroidViewModel {
    public static final String CONNECT_KEY = "connect_switch";
    public static final String PING_CMD = "ping_cmd";

    class ChanPair extends Pair<MutableLiveData<String>, MutableLiveData<String>> {

        public ChanPair() {
            super(new MutableLiveData<>(), new MutableLiveData<>());
        }
    }

    private Map<String, ChanPair> mSwitcher = new HashMap<>();

    public ControlsLivedataDispatcher(@NonNull Application application) {
        super(application);
    }

    public LiveData<String> addData(final String key, String value, Boolean fromView) {
        if (!mSwitcher.containsKey(key) || mSwitcher.get(key) == null) {
            addData(key);
        }
        ChanPair pair = mSwitcher.get(key);
        if (fromView) {
            pair.first.postValue(value);
            return pair.second;
        } else {
            pair.second.postValue(value);
            return pair.first;
        }

    }

    public void addData(final String key) {
        mSwitcher.put(key, new ChanPair());
    }

    public MutableLiveData<String> getData(final String key, Boolean fromView) {
        if (!mSwitcher.containsKey(key)) {
            addData(key);
        }
        return fromView ? mSwitcher.get(key).first : mSwitcher.get(key).second;
    }
}