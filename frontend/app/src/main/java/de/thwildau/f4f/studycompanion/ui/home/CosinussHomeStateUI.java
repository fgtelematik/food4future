package de.thwildau.f4f.studycompanion.ui.home;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

import androidx.core.content.ContextCompat;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.Utils.Observer;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.sensors.cosinuss.CosinussSensorManager;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;

public class CosinussHomeStateUI {
    // Instance state:
    private CosinussSensorManager sensorManager;
    private Context context = null;

    // UI Elements
    private View pairedView;
    private View unpairedView;
    private View statusNotConnectedView;
    private View statusConnectedView;
    private TextView stateDescriptionText;
    private TextView statusConnectedText;
    private View heartRateView;
    private TextView heartRateText;
    private View temperatureView;
    private TextView temperatureText;
    private View positioningQualityView;
    private TextView positioningQualityText;
    private ProgressBar positioningQualityProgressBar;
    private TextView positioningQualityWarningText;
    private View lowBatteryView;
    private TextView lowBatteryText;
    private View batteryLevelView;
    private TextView batteryLevelText;

    CosinussHomeStateUI(Context context, View homeFragmentView) {
        pairedView = homeFragmentView.findViewById(R.id.sensorCosinussPairedView);
        unpairedView = homeFragmentView.findViewById(R.id.sensorCosinussUnpairedView);
        statusNotConnectedView = homeFragmentView.findViewById(R.id.sensorCosinussStatusNotConnectedView);
        statusConnectedView = homeFragmentView.findViewById(R.id.sensorCosinussStatusConnectedView);
        stateDescriptionText = homeFragmentView.findViewById(R.id.sensorCosinussStateDescription);
        statusConnectedText = homeFragmentView.findViewById(R.id.sensorCosinussStatusConnectedText);
        heartRateView = homeFragmentView.findViewById(R.id.sensorCosinussHeartRateView);
        heartRateText = homeFragmentView.findViewById(R.id.sensorCosinussHeartRateText);
        temperatureView = homeFragmentView.findViewById(R.id.sensorCosinussTemperatureView);
        temperatureText = homeFragmentView.findViewById(R.id.sensorCosinussTemperatureText);
        positioningQualityView = homeFragmentView.findViewById(R.id.sensorCosinussPositioningQualityView);
        positioningQualityText = homeFragmentView.findViewById(R.id.sensorCosinussPositioningQualityText);
        positioningQualityProgressBar = homeFragmentView.findViewById(R.id.sensorCosinussPositioningQualityProgressBar);
        positioningQualityWarningText = homeFragmentView.findViewById(R.id.sensorCosinussPositioningQualityWarningText);
        lowBatteryView = homeFragmentView.findViewById(R.id.sensorCosinussLowBatteryView);
        lowBatteryText = homeFragmentView.findViewById(R.id.sensorCosinussLowBatteryText);
        batteryLevelText = homeFragmentView.findViewById(R.id.sensorCosinussBatteryLevelText);
        batteryLevelView = homeFragmentView.findViewById(R.id.sensorCosinussBatteryLevelView);

        sensorManager = StudyCompanion.getCosinussSensorManager();
        this.context = context;
    }

    private final Observer<Integer> positioningQualityObserver = (object, positioningQuality) -> {
        boolean available = positioningQuality != null;
        boolean showWarning = false;

        if(available) {
            int colorBadSignal = context.getColor(R.color.colorRed);
            int colorMediumSignal = context.getColor(R.color.colorLightGreen);
            int colorGoodSignal = context.getColor(R.color.colorGreen);
            int signalQualityLabelRes;

            int color;
            if (positioningQuality < 50) {
                showWarning = true;
                color = colorBadSignal;
                 signalQualityLabelRes = R.string.sensor_positioning_label_bad;
            } else if (positioningQuality < 100) {
                color = colorMediumSignal;
                signalQualityLabelRes = R.string.sensor_positioning_label_medium;
            } else {
                color = colorGoodSignal;
                signalQualityLabelRes = R.string.sensor_positioning_label_good;
            }

            positioningQualityProgressBar.setProgress(positioningQuality);
            positioningQualityProgressBar.setMax(50);
            positioningQualityProgressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            positioningQualityText.setText(Utils.getText(context, R.string.sensor_positioning, context.getString(signalQualityLabelRes)));
        }

        positioningQualityText.setVisibility(available ? View.VISIBLE : View.GONE);
        positioningQualityProgressBar.setVisibility(available ? View.VISIBLE : View.GONE);
        positioningQualityWarningText.setVisibility(showWarning ? View.VISIBLE : View.GONE);
        positioningQualityView.setVisibility(available ? View.VISIBLE : View.GONE);
    };


    private final Observer<Integer> batteryLevelObserver = (object, batteryLevel) -> {
        int threshold = SchemaProvider.getDeviceConfig().getLowBatteryLevel();
        lowBatteryView.setVisibility( batteryLevel != null && batteryLevel <= threshold ? View.VISIBLE:View.GONE);
        lowBatteryText.setText(Utils.getText(context, R.string.sensor_low_battery, batteryLevel != null ? batteryLevel : 0));
        batteryLevelView.setVisibility(batteryLevel == null ? View.GONE : View.VISIBLE);
        if(batteryLevel != null)
            batteryLevelText.setText(context.getString(R.string.sensor_battery_level, batteryLevel));
    };


    private final Observer<SensorConnectionState> connectionStateObserver = (object, connectionState) -> {
        boolean connected = connectionState.equals(SensorConnectionState.CONNECTED);
        statusConnectedView.setVisibility(connected ? View.VISIBLE : View.GONE);
        statusNotConnectedView.setVisibility(!connected ? View.VISIBLE : View.GONE);

        ISensorDevice device = sensorManager.getObservableCurrentDevice().getValue();
        String deviceName = device == null ? "" : device.getName();

        statusConnectedText.setText(Utils.getText(context, R.string.sensor_connected_to, deviceName));
    };

    private final Observer<ISensorDevice> currentDeviceObserver = (object, currentDevice) -> {
        boolean paired = currentDevice != null;
        pairedView.setVisibility(paired ? View.VISIBLE : View.GONE);
        unpairedView.setVisibility(!paired ? View.VISIBLE : View.GONE);
    };

    private final Observer<Float> bodyTemperatureObserver = (object, bodyTemperature) -> {
        boolean available = bodyTemperature != null ;
        temperatureView.setVisibility(available ? View.VISIBLE : View.INVISIBLE);
        temperatureText.setText(Utils.getText(context, R.string.sensor_temperature, available ? bodyTemperature : 0f));
    };

    private final Observer<Integer> heartRateObserver = (object, heartRate) -> {
        boolean available = heartRate != null ;
        heartRateView.setVisibility(available ? View.VISIBLE : View.INVISIBLE);
        heartRateText.setText(Utils.getText(context, R.string.sensor_heart_rate, available ?heartRate : 0));
    };


    public void onResume() {
        Executor executor = ContextCompat.getMainExecutor(context);

        sensorManager.getObservablePositioningQuality().addObserver(positioningQualityObserver, true, executor);
        sensorManager.getObservableBatteryLevel().addObserver(batteryLevelObserver, true, executor);
        sensorManager.getObservableConnectionState().addObserver(connectionStateObserver, true, executor);
        sensorManager.getObservableCurrentDevice().addObserver(currentDeviceObserver, true, executor);
        sensorManager.getObservableBodyTemperature().addObserver(bodyTemperatureObserver, true, executor);
        sensorManager.getObservableHeartRate().addObserver(heartRateObserver, true, executor);


        // fill placeholders of cosinuss state description Text...

        int wearingDuration = Utils.getMinutesFromMilitaryTimeDuration(SchemaProvider.getDeviceConfig().getCosinussWearingTimeDuration());
        wearingDuration = wearingDuration == 0 ? 30 : wearingDuration;

        String startTime = "18:00";
        Date startTimeDate = Utils.todayTimeFromMilitaryTime(SchemaProvider.getDeviceConfig().getCosinussWearingTimeBegin());
        if(startTimeDate != null) {
            startTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(startTimeDate);
        }

        String endTime = "23:00";
        Date endTimeDate = Utils.todayTimeFromMilitaryTime(SchemaProvider.getDeviceConfig().getCosinussWearingTimeEnd());
        if(endTimeDate != null) {
            endTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(endTimeDate);
        }

        stateDescriptionText.setText( context.getString(R.string.sensor_cosinuss_state_description, wearingDuration, startTime, endTime));
    }

    public void onPause() {
        sensorManager.getObservablePositioningQuality().removeObserver(positioningQualityObserver);
        sensorManager.getObservableBatteryLevel().removeObserver(batteryLevelObserver);
        sensorManager.getObservableConnectionState().removeObserver(connectionStateObserver);
        sensorManager.getObservableCurrentDevice().removeObserver(currentDeviceObserver);
        sensorManager.getObservableBodyTemperature().removeObserver(bodyTemperatureObserver);
        sensorManager.getObservableHeartRate().removeObserver(heartRateObserver);
    }
}
