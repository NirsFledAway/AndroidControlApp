package com.tamerlanchik.robocar.control_screen.vehicle_config;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tamerlanchik.robocar.R;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogItem;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
import com.tamerlanchik.robocar.control_screen.log_subsystem.Logger;

import java.text.SimpleDateFormat;
import java.util.List;

public class VehicleConfigFragment extends Fragment {
    private final String TAG = "ConfigFragment";
    Logger mLogger;
    VehicleConfig mConfig;

    // UI Views
    RecyclerView mConfigList;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config_edit, container, false);

        mConfig = new ViewModelProvider(requireActivity()).get(VehicleConfig.class);
        ConfigAdapter adapter = new ConfigAdapter(mConfig, getContext());

        mConfigList = view.findViewById(R.id.config_recycle_view);
        mConfigList.setLayoutManager(new LinearLayoutManager(getContext()));
        mConfigList.setAdapter(adapter);

        return view;
    }
}

class ConfigHolder extends RecyclerView.ViewHolder{
    private TextView mNameTextView;
    private TextView mEdit;

    public ConfigHolder(LayoutInflater inflater, ViewGroup parent) {
        super(inflater.inflate(R.layout.config_list_item, parent, false));
        mNameTextView = itemView.findViewById(R.id.config_param_name);
        mEdit = itemView.findViewById(R.id.config_param_edit);
    }

    public void bind(VehicleConfig.VehicleConfigItem item){
        mNameTextView.setText(item.mName);
        mEdit.setText(item.mValue);
    }
}

class ConfigAdapter extends RecyclerView.Adapter<ConfigHolder>{
    VehicleConfig mConfigList;
    Context mContext;

    public ConfigAdapter(VehicleConfig config, Context context){
        mConfigList = config;
        mContext = context;
    }

    @NonNull
    @Override
    public ConfigHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        return new ConfigHolder(inflater, parent);
    }

    @Override
    public void onBindViewHolder(@NonNull ConfigHolder holder, int position) {
        VehicleConfig.VehicleConfigItem item = mConfigList.getByPosition(position);
        holder.bind(item);

    }

    @Override
    public int getItemCount() {
        return mConfigList.size();
    }
}