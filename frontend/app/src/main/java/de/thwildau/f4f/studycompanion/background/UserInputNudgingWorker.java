package de.thwildau.f4f.studycompanion.background;

import android.content.Context;

import org.json.JSONObject;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;

public class UserInputNudgingWorker extends WorkerBase {
    public UserInputNudgingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        try {
            JSONObject todaysQuestions = DataManager.getUserDataForEffectiveDay(new Date());
            if(     Utils.isInStudyPeriod(new Date()) &&
                    Utils.getUserInputState(todaysQuestions) != Utils.UserInputState.COMPLETE_DATA) {
                NotificationOrganizer.showUserInputReminder();
            }
        } catch (DataManager.NoPermissionException e) {
            // do not show user input reminder if not logged in or not participant
        }

        return Result.success();
    }
}
