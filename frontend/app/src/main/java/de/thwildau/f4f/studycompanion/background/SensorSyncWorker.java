package de.thwildau.f4f.studycompanion.background;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;

public class SensorSyncWorker extends WorkerBase {
    private static final String LOG_TAG="SensorSyncWorker";

    public SensorSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
//        if(StudyCompanion.getGarminSensorManager().getObservableSynchronizationState().getValue().isSynchronizationActive())
//        {
//            BackendIO.serverLog(Log.ERROR, LOG_TAG,"Attempting to background sync with wearable sensors but is already syncing.");
//            return Result.success();
//        }
//
//        if(SchemaProvider.getDeviceConfig().isGarminUsed()) {
//            BackendIO.serverLog(Log.INFO, LOG_TAG, "Initiate background sync with Garmin sensor...");
//            StudyCompanion.getGarminSensorManager().startSynchronization();
//        }

        return Result.success();
    }
}
