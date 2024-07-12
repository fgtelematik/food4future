package de.thwildau.f4f.studycompanion.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

public class KeepAliveWorker extends WorkerBase {
    @Override
    public Result doWork() {
        // This service does nothing but forcing Android to keep the static App context alive
        // and reinitialize the SensorManagers if it was destroyed.
        // This is necessary to also keep the Bluetooth GATT server listening for BLE devices
        // currently used for discovering the Cosinuss one nearby.
        return Result.success();
    }

    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
}
