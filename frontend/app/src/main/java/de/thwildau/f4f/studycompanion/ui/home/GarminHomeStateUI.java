//package de.thwildau.f4f.studycompanion.ui.home;
//
//import android.content.Context;
//import android.view.View;
//import android.widget.Button;
//import android.widget.TextView;
//
//import java.text.DateFormat;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.concurrent.Executor;
//
//import androidx.core.content.ContextCompat;
//import de.thwildau.f4f.studycompanion.R;
//import de.thwildau.f4f.studycompanion.StudyCompanion;
//import de.thwildau.f4f.studycompanion.Utils;
//import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
//import de.thwildau.f4f.studycompanion.sensors.garmin.GarminSensorManager;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorSynchronizationState;
//
//public class GarminHomeStateUI {
//    private Context context;
//    private View rootView;
//    private GarminSensorManager sensorManager;
//
//    GarminHomeStateUI(Context context, View homeFragmentView) {
//        rootView = homeFragmentView;
//        this.context = context;
//        sensorManager = StudyCompanion.getGarminSensorManager();
//    }
//
//    private final Utils.Observer<SensorSynchronizationState> sensorSynchronizationStateObserver = (object, data) -> updateLastSyncUI(data.getLastSyncTime());
//
//    private final Utils.Observer<SensorConnectionState> sensorConnectionStateObserver = new Utils.Observer<SensorConnectionState>() {
//        @Override
//        public void onUpdate(Utils.ObservableValue<SensorConnectionState> object, SensorConnectionState data) {
//            View connectedView = rootView.findViewById(R.id.sensorGaminStatusConnectedView);
//            View disconnectedView = rootView.findViewById(R.id.sensorGaminStatusNotConnectedView);
//            Button buttonSensorManagement = rootView.findViewById(R.id.buttonManageSensorGarmin);
//            TextView connectedText = rootView.findViewById(R.id.sensorGarminStatusConnectedText);
//
//            boolean connected = (data == SensorConnectionState.CONNECTED);
//
//            connectedView.setVisibility(connected ? View.VISIBLE : View.GONE);
//            disconnectedView.setVisibility(!connected ? View.VISIBLE : View.GONE);
//            buttonSensorManagement.setVisibility(!connected ? View.VISIBLE : View.GONE);
//
//            ISensorDevice sensorDevice = sensorManager.getCurrentSensorDevice();
//            String sensorName = "--";
//            if(sensorDevice != null) {
//                sensorName = sensorDevice.getName();
//            }
//
//            connectedText.setText(context.getString(R.string.sensor_connected_to, sensorName));
//        }
//    };
//
//    private final Utils.Observer<ISensorDevice> currentDeviceObserver = new Utils.Observer<ISensorDevice>() {
//        @Override
//        public void onUpdate(Utils.ObservableValue<ISensorDevice> object, ISensorDevice device) {
//            boolean paired = device != null;
//            rootView.findViewById(R.id.sensorGarminPairedView).setVisibility(paired ? View.VISIBLE : View.GONE);
//            rootView.findViewById(R.id.sensorGarminUnpairedView).setVisibility(!paired ? View.VISIBLE : View.GONE);
//        }
//    };
//
//    private void updateLastSyncUI(Date lastSyncTime) {
//        boolean syncUpToDate = false;
//
//        View syncOutdatedView = rootView.findViewById(R.id.sensorSyncOutdatedView);
//        View syncUpToDateView = rootView.findViewById(R.id.sensorSyncUpToDateView);
//
//        TextView syncOutdatedText = rootView.findViewById(R.id.sensorSyncOutdatedText);
//        TextView syncUpToDateText = rootView.findViewById(R.id.sensorSyncUpToDateText);
//
//        if(lastSyncTime == null) {
//            syncOutdatedText.setText(R.string.sensor_never_synched);
//        } else {
//            long differenceMillis = new Date().getTime() - lastSyncTime.getTime();
//            int minutesAgo = (int) (differenceMillis / (1000*60));
//
//            int maxMinutesAgo = SchemaProvider.getDeviceConfig().getSensorSyncMaxAgeMinutes();
//
//            if(minutesAgo > maxMinutesAgo) {
//                syncOutdatedText.setText(context.getString(R.string.sensor_unsynched, maxMinutesAgo / 60));
//            } else {
//                String syncTimeStr = SimpleDateFormat.getTimeInstance(DateFormat.SHORT).format(lastSyncTime);
//                syncUpToDateText.setText(context.getString(R.string.sensor_synched, syncTimeStr));
//                syncUpToDate = true;
//            }
//        }
//
//        syncUpToDateView.setVisibility(syncUpToDate ? View.VISIBLE : View.GONE);
//        syncOutdatedView.setVisibility(!syncUpToDate  ? View.VISIBLE : View.GONE);
//    }
//
//
//    private final Utils.Observer<Integer> batteryLevelObserver = new Utils.Observer<Integer>() {
//        @Override
//        public void onUpdate(Utils.ObservableValue<Integer> object, Integer batteryLevel) {
//            View batteryLowView = rootView.findViewById(R.id.sensorGarminLowBatteryView);
//            TextView batteryLowText = rootView.findViewById(R.id.sensorGarminLowBatteryText);
//            int threshold = SchemaProvider.getDeviceConfig().getLowBatteryLevel();
//            batteryLowView.setVisibility( batteryLevel != null && batteryLevel <= threshold ? View.VISIBLE:View.GONE);
//            batteryLowText.setText(Utils.getText(context, R.string.sensor_low_battery, batteryLevel != null ? batteryLevel : 0));
//        }
//    };
//
//
//    public void onResume() {
//        Executor executor = ContextCompat.getMainExecutor(context);
//
//        sensorManager.getObservableSynchronizationState().addObserver(sensorSynchronizationStateObserver, true, executor);
//        sensorManager.getObservableConnectionState().addObserver(sensorConnectionStateObserver, true, executor);
//        sensorManager.getObservableCurrentDevice().addObserver(currentDeviceObserver, true, executor);
//        sensorManager.getObservableBatteryLevel().addObserver(batteryLevelObserver, true, executor);
//    }
//
//    public void onPause() {
//        sensorManager.getObservableSynchronizationState().removeObserver(sensorSynchronizationStateObserver);
//        sensorManager.getObservableConnectionState().removeObserver(sensorConnectionStateObserver);
//        sensorManager.getObservableCurrentDevice().removeObserver(currentDeviceObserver);
//        sensorManager.getObservableBatteryLevel().removeObserver(batteryLevelObserver);
//    }
//}
