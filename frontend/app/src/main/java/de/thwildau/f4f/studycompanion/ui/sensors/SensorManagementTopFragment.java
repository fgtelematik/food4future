package de.thwildau.f4f.studycompanion.ui.sensors;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;


public class SensorManagementTopFragment extends Fragment {
    private List<String> tabLabels;

    private Fragment managementFragmentGarmin = null;
    private Fragment managementFragmentCosinuss = null;

    public SensorManagementTopFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tabLabels = SchemaProvider.getDeviceConfig().getSensorsUsed();

        // Create fragment instances
//        if(managementFragmentGarmin == null && tabLabels.contains("Garmin")) {
//            managementFragmentGarmin = SensorManagementFragment.newInstance(SensorManagementFragment.SensorType.Garmin);
//        }
        if(managementFragmentCosinuss == null  && tabLabels.contains("Garmin")) {
            managementFragmentCosinuss = SensorManagementFragment.newInstance(SensorManagementFragment.SensorType.Cosinuss);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sensor_management_top, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SensorManagementFragmentStateAdapter fragmentStateAdapter = new SensorManagementFragmentStateAdapter(this);
        TabLayout tabLayout = view.findViewById(R.id.sensor_management_tab_layout);
        ViewPager2 viewPager = view.findViewById(R.id.sensor_management_view_pager);
        viewPager.setAdapter(fragmentStateAdapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> { tab.setText(tabLabels.get(position)); } ).attach();
        Bundle bundle = getArguments();
        if(bundle != null && bundle.containsKey(SensorManagementFragment.EXTRA_SENSOR_TYPE)) {
            SensorManagementFragment.SensorType sensorType = SensorManagementFragment.SensorType.valueOf(bundle.getString(SensorManagementFragment.EXTRA_SENSOR_TYPE));
            int activeTabIndex = tabLabels.indexOf(sensorType.toString());
            if(activeTabIndex >= 0) {
                new Handler().post(() ->
                        // page needs to be switched on next UI cycle, otherwise viewPager.setCurrentItem() would be ignored.
                        // see: https://stackoverflow.com/a/23363016/5106474
                {
                    tabLayout.getTabAt(activeTabIndex).select();
                });
            }
        }
    }

    private static class SensorManagementFragmentStateAdapter extends FragmentStateAdapter {
        SensorManagementTopFragment fragmentInstance;
        public SensorManagementFragmentStateAdapter(@NonNull Fragment fragment) {

            super(fragment);
            fragmentInstance = (SensorManagementTopFragment)fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            SensorManagementFragment.SensorType sensorType = SensorManagementFragment.SensorType.valueOf(fragmentInstance.tabLabels.get(position));
            return SensorManagementFragment.newInstance(sensorType);
        }

        @Override
        public int getItemCount() {
            return fragmentInstance.tabLabels.size();
        }
    }


}