package de.thwildau.f4f.studycompanion.background;

import android.content.Context;
import android.util.Log;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;

public class CosinussNudgingWorker extends WorkerBase {
    private static final String LOG_TAG = "CosinussNudgingWorker";
    public CosinussNudgingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        // show a notification to remind user of wearing the cosinuss° One sensor today,
        // but only if the sensor was not already connected after the beginning time
        // of the daily wearing period.

        if(!SchemaProvider.getDeviceConfig().isCosinussUsed() || !Utils.isInStudyPeriod(new Date())) {
            // not in study period or Cosinuss sensor is not used in current study. Do nothing.
            return Result.success();
        }

        Date lastSyncDate = StudyCompanion.getCosinussSensorManager().getObservableSynchronizationState().getValue().getLastSyncTime();

        long lastSyncDateMs = 0;
        if(lastSyncDate != null) {
            lastSyncDateMs = lastSyncDate.getTime();
        }

        Date beginningWearingTimeToday = Utils.todayTimeFromMilitaryTime(SchemaProvider.getDeviceConfig().getCosinussWearingTimeBegin());
        if(beginningWearingTimeToday == null) {
            // invalid or empty device config value specified. No nudging notification will be displayed.
            return Result.success();
        }

        if(lastSyncDateMs < beginningWearingTimeToday.getTime()) {
            // Sensor was not yet connected after today's wearing period start time
            NotificationOrganizer.showCosinussReminder();
        }

        return Result.success();
    }
}
