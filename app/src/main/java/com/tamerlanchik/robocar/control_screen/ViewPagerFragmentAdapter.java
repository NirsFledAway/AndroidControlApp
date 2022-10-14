package com.tamerlanchik.robocar.control_screen;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tamerlanchik.robocar.control_screen.log_subsystem.LogFragment;
import com.tamerlanchik.robocar.control_screen.log_subsystem.LogStorage;
import com.tamerlanchik.robocar.control_screen.vehicle_config.VehicleConfigFragment;

class ViewPagerFragmentAdapter extends FragmentStateAdapter {
    static final String TAG = "ViewPagerFragmentAdap..";

    public ViewPagerFragmentAdapter(FragmentActivity ctx) {
        super(ctx);
    }


    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new LogFragment();
                Log.d(TAG, "Create LogFragment");
                break;
            case 1:
                fragment = new VehicleConfigFragment();
                Log.d(TAG, "Create VehicleConfigFragment");
                break;
            default:
                fragment = null;
                Log.e(TAG, "Wrong position: " + Integer.toString(position));
        }
//        Bundle args = new Bundle();
//        args.putInt("viewpager_textview", position + 1);
//        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
