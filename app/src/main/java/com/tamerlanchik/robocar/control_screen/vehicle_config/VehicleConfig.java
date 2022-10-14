package com.tamerlanchik.robocar.control_screen.vehicle_config;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleConfig extends ViewModel {
    private MutableLiveData<VehicleConfigItem> mConfigItemLiveData;
    private List<VehicleConfigItem> mConfig = new ArrayList<>();
    public class VehicleConfigItem {
        public String mName = "";
        public String mValue = "";
        public VehicleConfigItem(String name, String value){
            mName = name; mValue = value;
        }
    }
    public VehicleConfig() {
        mConfigItemLiveData = new MutableLiveData<>();
        initConfig();
    }

    private void initConfig() {
//        final Map<String, String> cfg_stub = Map.of(
//            "thrust_Kp", "10",
//            "thrust_Ki", "20",
//            "thrust_Kd", "5"
//        );
        mConfig.add(new VehicleConfigItem("thrust_Kp", "10"));
        mConfig.add(new VehicleConfigItem("thrust_Ki", "20"));
        mConfig.add(new VehicleConfigItem("thrust_Kd", "5"));
    }

    public VehicleConfigItem getByPosition(int pos) {
        return mConfig.get(pos);
    }

    public int size() {
        return mConfig.size();
    }

    public final List<VehicleConfigItem> getList() {
        return mConfig;
    }
}
