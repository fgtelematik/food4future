package de.thwildau.f4f.studycompanion.sensors;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;

public class BluetoothConnectionStateBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SensorConnectionState connectionState;



        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            connectionState = SensorConnectionState.CONNECTED;
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            connectionState = SensorConnectionState.DISCONNECTED;
        } else {
            return;
        }

        BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        //BackendIO.serverLog("Received Bt Broadcast: '"  + btDevice.getName() +"' " + connectionState);

        for(SensorManagerBase sensorManager : StudyCompanion.getSensorManagers()) {
            sensorManager.notifyBluetoothConnectionStateChange(btDevice, connectionState);
        }
    }
}
