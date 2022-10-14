package com.tamerlanchik.robocar.control_screen.log_subsystem;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.tamerlanchik.robocar.R;

public class LogFragment extends Fragment {
    private final String TAG = "LogFragment";
    Logger mLogger;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        mLogger = new Logger(requireActivity());
        LogStorage logs = new ViewModelProvider(requireActivity()).get(LogStorage.class);
        mLogger.create(view, logs);
        logs.getLiveData().observe(requireActivity(), new Observer<LogItem>() {
            @Override
            public void onChanged(LogItem logItem) {
                mLogger.writeOnUiThread(logItem);
            }
        });
        return view;
    }
}

