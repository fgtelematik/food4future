package de.thwildau.f4f.studycompanion.ui.sensors;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.sensors.SensorManagerBase;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorSynchronizationState;
import de.thwildau.f4f.studycompanion.ui.ProgressDialog;

public class SensorManagementFragment extends Fragment {

    private static final int NOT_CONNECTED_DELAY_MS = 10000; // Amount of milliseconds, after the "not connected to device" warning appears.
    private static final int CRITICAL_BATTERY_LEVEL_THRESHOLD = 25; // Maximum percentage of sensor battery level on which the user will be asked to recharge device

    public enum SensorType {
//        Garmin,
        Cosinuss
    }

    public static final String EXTRA_SENSOR_TYPE = "EXTRA_SENSOR_TYPE";

    private SensorManagementViewModel mViewModel;
    private View rootView;
    private Button buttonSync;
    private Button buttonUnpair;

    private ViewGroup scannedDevicesList;
    private SensorListAdapter scannedDevicesAdapter;

    private ProgressDialog pairingProgressDialog;
    private AlertDialog resetConfirmationDialog = null;

    private SensorManagerBase sensorManager = null;
    private SensorType sensorType;
    private boolean wasScanning = false;

    public static SensorManagementFragment newInstance(SensorType sensorType) {
        SensorManagementFragment fragment = new SensorManagementFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_SENSOR_TYPE, sensorType.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if(args != null) {
            String sensorTypeStr = args.getString(EXTRA_SENSOR_TYPE);
            if(sensorTypeStr != null) {
                try {
                    sensorType = SensorType.valueOf(sensorTypeStr);
                    switch(sensorType) {
                        case Cosinuss:
                            sensorManager = StudyCompanion.getCosinussSensorManager();
                            break;
//                        case Garmin:
//                            sensorManager=StudyCompanion.getGarminSensorManager();
//                            break;
                    }
                } catch(IllegalArgumentException e) {
                    //
                }
            }
        }

        if(sensorManager == null) {
            throw new IllegalArgumentException("Tried to instantiate SensorManagementFragment without specifying a valid EXTRA_SENSOR_TYPE");
        }
    }

    private final ArrayList<View> entryViews = new ArrayList<>();;

    // We self-implement the ArrayAdapter functionality here to be able to have a non-scrollable
    // ListView.
    private void updateScannedDevicesListUI() {
        scannedDevicesList.removeAllViews();
        List<ISensorDevice> deviceList = mViewModel.getScannedDevices().getValue();
        if(deviceList == null) return;

        int numDevices = deviceList.size();
        int numEntryViews = entryViews.size();
        int i = 0;
        for(; i < numDevices; i++) {
            View entryView = null;
            if(numEntryViews > i) {
                entryView = entryViews.get(i);
            }
            entryView = scannedDevicesAdapter.getView(i, entryView, scannedDevicesList);
            scannedDevicesList.addView(entryView);
            final int elementIndex = i;
            entryView.setOnClickListener((v) -> onScannedDeviceClicked(elementIndex));


            if(numEntryViews <= i) {
                entryViews.add(entryView);
            } else {
                entryViews.set(i, entryView);
            }
        }

        ArrayList<View> currentEntryViews = new ArrayList<>(entryViews);

        // Remove old elements with index > number devices
        for(;i < numEntryViews; i++) {
            View entryView = currentEntryViews.get(i);
            entryViews.remove(entryView);
        }


    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_sensor_management, container, false);

        buttonSync = rootView.findViewById(R.id.buttonSynchSensor);
        buttonUnpair = rootView.findViewById(R.id.buttonUnpair);

        buttonSync.setOnClickListener(v -> {
            mViewModel.startSync();
            buttonSync.setEnabled(false);
        });

        buttonUnpair.setOnClickListener(this::onButtonUnpairPressed);

        scannedDevicesList = rootView.findViewById(R.id.scannedDevicesList);

        TextView textSensorTitle = rootView.findViewById(R.id.sensorTitleTextView);
        switch(sensorType) {
//            case Garmin:
//                textSensorTitle.setText(R.string.sensor_title_garmin);
//                break;
            case Cosinuss:
                textSensorTitle.setText(R.string.sensor_title_cosinuss);
                break;
        }

        init();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mViewModel.release();
    }

    private void init() {
        mViewModel = new ViewModelProvider(this).get(SensorManagementViewModel.class);

        mViewModel.getBatteryLevel().observe(getViewLifecycleOwner(), this::onUpdateBatteryLevel);
        mViewModel.getConnectionState().observe(getViewLifecycleOwner(), this::onUpdateConnectionState);
        mViewModel.getPairedDevice().observe(getViewLifecycleOwner(), this::onUpdatePairedDevice);
        mViewModel.getPairing().observe(getViewLifecycleOwner(), this::onUpdatePairing);
        mViewModel.getScannedDevices().observe(getViewLifecycleOwner(), this::onUpdateScannedDevices);
        mViewModel.getScanning().observe(getViewLifecycleOwner(), this::onUpdateScanning);
        mViewModel.getSyncState().observe(getViewLifecycleOwner(), this::onUpdateSyncState);
        mViewModel.getToastMessage().observe(getViewLifecycleOwner(), this::onMessage);
        mViewModel.getAuthCodeRequested().observe(getViewLifecycleOwner(), this::onAuthCodeRequested);
        mViewModel.getResetRequested().observe(getViewLifecycleOwner(), this::onResetRequested);
        mViewModel.getResetRequestCancelled().observe(getViewLifecycleOwner(), this::onResetRequestCancelled);
        mViewModel.getBluetoothEnabled().observe(getViewLifecycleOwner(), this::onBluetoothStateChanged);
        mViewModel.getGarminSdkInitializationError().observe(getViewLifecycleOwner(), this::onUpdateGarminSdkError);

        mViewModel.init(sensorManager);

        scannedDevicesAdapter = new SensorListAdapter(getContext(), mViewModel.getScannedDevices().getValue());
    }

    private void onBluetoothStateChanged(Boolean btEnabled) {
        if(btEnabled == null) {
            return;
        }
        rootView.findViewById(R.id.bluetoothDisabledView).setVisibility(btEnabled ? View.GONE:View.VISIBLE );
        onUpdateConnectionState(mViewModel.getConnectionState().getValue());
    }

    private void onResetRequestCancelled(Boolean resetRequestCancelled) {
        if(resetRequestCancelled && resetConfirmationDialog != null) {
            resetConfirmationDialog.cancel();
            resetConfirmationDialog = null;
        }
    }

    private void onAuthCodeRequested(Boolean authCodeRequested) {
        if(!authCodeRequested) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.sensor_pair_enter_auth_code);

        final EditText input = new EditText(getContext());

        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String text = input.getText().toString();
            int passkey = Integer.parseInt(text);
            mViewModel.setAuthCode(passkey);
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onResume() {
        super.onResume();

        if(wasScanning && mViewModel != null) {
            mViewModel.startScanning();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mViewModel != null) {
            Boolean isScanning = mViewModel.getScanning().getValue();
            wasScanning = isScanning != null && isScanning;
            mViewModel.stopScanning();
        }
    }

    private void onResetRequested(Boolean resetRequested) {
        if(!resetRequested) {
            return;
        }

        resetConfirmationDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.sensor_pair_reset_requested_title)
                .setMessage(R.string.sensor_pair_reset_requested_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    mViewModel.answerResetRequest(true);
                    resetConfirmationDialog = null;
                })
                .setNegativeButton(R.string.no, (dialog, which) -> {
                    mViewModel.answerResetRequest(false);
                    resetConfirmationDialog = null;
                })
                .setCancelable(false)
                .create();

        resetConfirmationDialog.show();

    }

    // -- UI  callbacks --
    private void onScannedDeviceClicked(int position) {
        ISensorDevice device = mViewModel.getScannedDevices().getValue().get(position);

        String pairingPin = device.getPairingPin();

        if(pairingPin == null) {
            mViewModel.pairWithDevice(device);
            return;
        }

        View dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_pairing_pin, null);
        TextView txtPin = dialogView.findViewById(R.id.txt_pin);
        txtPin.setText(pairingPin);

        // Pairing requires a pin. Show pin to user before pairing.
        new AlertDialog.Builder(getActivity())
                .setView(dialogView)
                .setTitle(R.string.sensor_dialog_pin_title)
                .setPositiveButton(R.string.ok, (dialog, which) -> mViewModel.pairWithDevice(device))
                .setCancelable(false)
                .show();

    }

    private void onButtonUnpairPressed(View view) {
        ISensorDevice currentDevice = mViewModel.getPairedDevice().getValue();
        if(currentDevice == null) {
            return;
        }

        String deviceName = currentDevice.getName();

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.sensor_unpair_title)
                .setMessage(getContext().getString(R.string.sensor_unpair_mesage, deviceName))
                .setPositiveButton(R.string.yes, (dialog, which) -> mViewModel.unpairDevice())
                .setNegativeButton(R.string.no, null)
                .setCancelable(true)
                .show();
    }

    // -- ViewModel Callbacks --
    private void onMessage(String message) {
        if(Utils.nullOrEmpty(message)) {
            return;
        }

        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private void onUpdateSyncState(SensorSynchronizationState syncState) {
        if(syncState == null) {
            return;
        }

        boolean syncActive = syncState.isSynchronizationActive();

        buttonSync.setEnabled(!syncActive);
        buttonUnpair.setEnabled(!syncActive);

        rootView.findViewById(R.id.sensorSyncActiveView).setVisibility(syncActive ? View.VISIBLE : View.GONE);

        Integer progress = syncState.getSynchronizationProgress();
        TextView progressText = rootView.findViewById(R.id.textSyncProgress);
        if(progress == null || progress == 0) {
            progressText.setVisibility(View.GONE);
        } else {
            progressText.setVisibility(View.VISIBLE);
            progressText.setText(progress + " %");
        }

        updateLastSyncUI(syncState.getLastSyncTime());
    }

    private void onUpdateScanning(Boolean scanning) {
        rootView.findViewById(R.id.scanningStatusView).setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    private void onUpdateScannedDevices(List<ISensorDevice> scannedDevices) {
        scannedDevicesAdapter.notifyDataSetInvalidated();
        updateScannedDevicesListUI();
    }

    private void onUpdatePairing(Boolean pairing) {
        if(pairing && pairingProgressDialog == null) {
            pairingProgressDialog = new ProgressDialog(getContext());
            pairingProgressDialog.setTitle(getString(R.string.sensor_pairing_dialog_message));
            pairingProgressDialog.setCancelable(false);
            pairingProgressDialog.setOnProgressDialogCancelListener(() -> {
                mViewModel.cancelPairing();
                return true;
            });
            pairingProgressDialog.show();
        } else if(!pairing && pairingProgressDialog != null) {
            pairingProgressDialog.dismiss();
            pairingProgressDialog = null;
        }
    }

    private void onUpdateGarminSdkError(Boolean sdkError) {
        View garminSdkErrorView = rootView.findViewById(R.id.garminSdkErrorView);
        View sensorPairedView = rootView.findViewById(R.id.sensorPairedView);
        View sensorUnpairedView = rootView.findViewById(R.id.sensorUnpairedView);

        garminSdkErrorView.setVisibility(sdkError ? View.VISIBLE:View.GONE);

        if(sdkError) {
            sensorPairedView.setVisibility(View.GONE);
            sensorUnpairedView.setVisibility(View.GONE);
        } else {
            onUpdatePairedDevice(mViewModel.getPairedDevice().getValue());
        }

    }

    private void onUpdatePairedDevice(ISensorDevice sensorDevice) {
        View sensorPairedView = rootView.findViewById(R.id.sensorPairedView);
        View sensorUnpairedView = rootView.findViewById(R.id.sensorUnpairedView);
        View scanningViewPaired = rootView.findViewById(R.id.scanningViewPaired);
        View scanningViewUnpaired = rootView.findViewById(R.id.scanningViewUnpaired);
        TextView sensorNameText = rootView.findViewById(R.id.sensorPairedSensorNameText);
        TextView sensorNameText2 = rootView.findViewById(R.id.sensorPairedSensorNameText2);
        TextView sensorMacText = rootView.findViewById(R.id.sensorPairedSensorMacText);

        int visiblePaired = sensorDevice == null ? View.GONE : View.VISIBLE;
        int visibleUnpaired = sensorDevice == null ? View.VISIBLE : View.GONE;

        sensorPairedView.setVisibility(visiblePaired);
        sensorUnpairedView.setVisibility(visibleUnpaired);
        scanningViewPaired.setVisibility(visiblePaired);
        scanningViewUnpaired.setVisibility(visibleUnpaired);

        if(sensorDevice != null) {
            String deviceName = sensorDevice.getName();
            String deviceMac = sensorDevice.getMacAddress();

            sensorNameText.setText(deviceName);
            sensorNameText2.setText(deviceName);
            sensorMacText.setText(deviceMac);
            mViewModel.stopScanning();
        } else {
            mViewModel.startScanning();
        }

        onUpdateConnectionState(mViewModel.getConnectionState().getValue());
        onUpdateSyncState(mViewModel.getSyncState().getValue());
    }

    private void onUpdateConnectionState(SensorConnectionState sensorConnectionState) {
        if(sensorConnectionState == null) {
            return;
        }

        boolean connected = sensorConnectionState == SensorConnectionState.CONNECTED;
        boolean paired = mViewModel.getPairedDevice().getValue() != null;
        boolean blEnabled = mViewModel.getBluetoothEnabled().getValue();

        rootView.findViewById(R.id.sensorGaminStatusConnectedView).setVisibility(connected ? View.VISIBLE : View.GONE);
//        rootView.findViewById(R.id.buttonSynchSensor).setVisibility(connected && sensorType == SensorType.Garmin ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.buttonSynchSensor).setVisibility(View.GONE);
        rootView.findViewById(R.id.sensorStatusSearchingView).setVisibility(!connected && paired && blEnabled ? View.VISIBLE : View.GONE);

        rootView.findViewById(R.id.sensorGaminStatusNotConnectedView).setVisibility(View.GONE);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if(mViewModel.getConnectionState().getValue() == SensorConnectionState.DISCONNECTED && mViewModel.getPairedDevice().getValue() != null && blEnabled) {
                rootView.findViewById(R.id.sensorGaminStatusNotConnectedView).setVisibility(View.VISIBLE);
            }
        }, NOT_CONNECTED_DELAY_MS);


        TextView connectionStatusText = rootView.findViewById(R.id.connectionStatusText);
        if(sensorConnectionState == SensorConnectionState.CONNECTING) {
            connectionStatusText.setText(R.string.sensor_connection_connecting);
        } else if(sensorConnectionState == SensorConnectionState.DISCONNECTED) {
            connectionStatusText.setText(R.string.sensor_connection_searching);
        }

    }

    private void onUpdateBatteryLevel(Integer value) {
        boolean levelAvailable = value != null;
        boolean levelCritical = false;
        if(value != null && value <= CRITICAL_BATTERY_LEVEL_THRESHOLD) {
            levelCritical = true;
        }
        String levelString = value == null ? getString(R.string.battery_level_unknown) : value + " %";
        int textColor = getContext().getColor(levelCritical ? R.color.colorOrange : R.color.colorGreen);

        View batteryLevelView = rootView.findViewById(R.id.batteryLevelView);
        ImageView batteryLevelGoodImage = rootView.findViewById(R.id.batteryLevelGoodImage);
        ImageView batteryLevelBadImage = rootView.findViewById(R.id.batteryLevelBadImage);
        TextView batteryLevelText = rootView.findViewById(R.id.batteryLevelText);
        TextView batteryLevelRechargeNoteText = rootView.findViewById(R.id.batteryLevelRechargeNoteText);

        batteryLevelView.setVisibility(levelAvailable ? View.VISIBLE : View.GONE);
        batteryLevelGoodImage.setVisibility(levelCritical ? View.GONE : View.VISIBLE);
        batteryLevelBadImage.setVisibility(levelCritical ? View.VISIBLE : View.GONE);
        batteryLevelRechargeNoteText.setVisibility(levelCritical ? View.VISIBLE : View.GONE);
        batteryLevelText.setTextColor(textColor);
        batteryLevelText.setText(getContext().getString(R.string.sensor_battery_level, value == null ? -1 : value));
    }

    // Helper Methods:

    private void updateLastSyncUI(Date lastSyncTime) {
        boolean syncUpToDate = false;
        boolean paired = mViewModel.getPairedDevice().getValue() != null;

        View syncOutdatedView = rootView.findViewById(R.id.sensorSyncOutdatedView);
        View syncUpToDateView = rootView.findViewById(R.id.sensorSyncUpToDateView);

        TextView syncOutdatedText = rootView.findViewById(R.id.sensorSyncOutdatedText);
        TextView syncUpToDateText = rootView.findViewById(R.id.sensorSyncUpToDateText);

        if(lastSyncTime == null) {
            syncOutdatedText.setText(R.string.sensor_never_synched);
        } else {
            long differenceMillis = new Date().getTime() - lastSyncTime.getTime();
             int hoursAgo = (int) (differenceMillis / (1000*60*60));

            int maxMinutesAgo = SchemaProvider.getDeviceConfig().getSensorSyncMaxAgeMinutes();

             if(hoursAgo > maxMinutesAgo) {
                 syncOutdatedText.setText(getContext().getString(R.string.sensor_unsynched, maxMinutesAgo / 60));
             } else {
                 String syncTimeStr = SimpleDateFormat.getTimeInstance(DateFormat.SHORT).format(lastSyncTime);
                 syncUpToDateText.setText(getContext().getString(R.string.sensor_synched, syncTimeStr));
                 syncUpToDate = true;
             }
        }

        syncUpToDateView.setVisibility(syncUpToDate && paired ? View.VISIBLE : View.GONE);
        syncOutdatedView.setVisibility(!syncUpToDate && paired ? View.VISIBLE : View.GONE);
    }

}