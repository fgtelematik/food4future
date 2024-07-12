package de.thwildau.f4f.studycompanion.background;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.DeviceConfig;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;

public abstract class WorkerBase extends ListenableWorker { private static final String LOG_TAG = "WorkerBase";

    private static final boolean VERBOSE_LOGGING = false; // for debugging only!

    private static final int DEFAULT_INTERVAL_MINUTES = 60;

    private static final String PREF_ACTIVE = "active";
    private static final String PREF_INTERVAL = "interval_minutes";

    private static final int CMD_MESSAGE = 1;

    private SettableFuture<Result> currentWorkResult;
    private static Handler uiHandler;

    public WorkerBase(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void initAllWorkers() {
        DeviceConfig config = SchemaProvider.getDeviceConfig();
        int dailyInterval = (int)TimeUnit.DAYS.toMinutes(1);

        // Configure Auto-sync server worker
        int serverAutoSyncInterval = config.getServerAutoSyncInterval();
        if(serverAutoSyncInterval > 0) {
            setGlobalIntervalMinutes(BackendSyncWorker.class, serverAutoSyncInterval);
            globalActivate(BackendSyncWorker.class, true, null);
        } else {
            globalDeactivate(BackendSyncWorker.class);
        }

        // Configure Auto-sync Garmin sensor worker
        int sensorAutoSyncInterval = config.getSensorAutoSyncInterval();
        if(sensorAutoSyncInterval > 0) {
            setGlobalIntervalMinutes(SensorSyncWorker.class, sensorAutoSyncInterval);
            globalActivate(SensorSyncWorker.class, false, null);
        } else {
            globalDeactivate(SensorSyncWorker.class);
        }

        // Configure Cosinuss wearing reminder notification worker
        Long cosinussReminderExecutionDelayMs = Utils.getMillisecondsTillNextMilitaryTime(SchemaProvider.getDeviceConfig().getCosinussWearingReminderTime());
        if(cosinussReminderExecutionDelayMs == null) {
            globalDeactivate(CosinussNudgingWorker.class);
        } else {
            Long delayMinutes = TimeUnit.MILLISECONDS.toMinutes(cosinussReminderExecutionDelayMs);
            setGlobalIntervalMinutes(CosinussNudgingWorker.class, dailyInterval);
            globalActivate(CosinussNudgingWorker.class, false, delayMinutes);
        }

        // Configure User Input reminder notification worker
        Long userInputReminderExecutionDelayMs = Utils.getMillisecondsTillNextMilitaryTime(SchemaProvider.getDeviceConfig().getFoodInputReminderTime());
        if(cosinussReminderExecutionDelayMs == null) {
            globalDeactivate(UserInputNudgingWorker.class);
        } else {
            Long delayMinutes = TimeUnit.MILLISECONDS.toMinutes(userInputReminderExecutionDelayMs);
            setGlobalIntervalMinutes(UserInputNudgingWorker.class, dailyInterval);
            globalActivate(UserInputNudgingWorker.class, false, delayMinutes);
        }

        // Configure Keep-Alive worker to be launched at the minimum time period of 15 minutes.
        setGlobalIntervalMinutes(KeepAliveWorker.class, 15);
        globalActivate(KeepAliveWorker.class, false, null);
    }

    private static <T extends WorkerBase> String getPrefName(Class<T> workerClass, String setting) {
        return workerClass.getName() + "_" + setting;
    }

    private static <T extends WorkerBase> String getWorkerName(Class<T> workerClass) {
        return workerClass.getName() + "_worker";
    }

    private static SharedPreferences getSp() {
        return PreferenceManager.getDefaultSharedPreferences(StudyCompanion.getAppContext());
    }

    protected static void toastMsg(String msg) {
        uiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                if(message.what == CMD_MESSAGE) {
                    Toast.makeText(StudyCompanion.getAppContext(), (String) message.obj, Toast.LENGTH_SHORT).show();
                }
            }
        };

        Message message = uiHandler.obtainMessage(CMD_MESSAGE, msg);
        message.sendToTarget();
    }


    public static <T extends WorkerBase> void globalActivate(Class<T> workerClass, boolean wifiRequired, Long customDelayMinutes) {
        getSp().edit().putBoolean(getPrefName(workerClass, PREF_ACTIVE), true).apply();
        int interval = getGlobalIntervalMinutes(workerClass);


        long delay = interval;
        if(customDelayMinutes != null) {
            delay = customDelayMinutes;
        }

        String logMsg = "Activating periodic Worker '" + getWorkerName(workerClass) + "' triggered every " + interval + " minutes, starting in " + delay + " minutes.";
        log(logMsg, true);

        PeriodicWorkRequest.Builder reqBuilder =new PeriodicWorkRequest.Builder(workerClass, interval, TimeUnit.MINUTES).setInitialDelay(delay, TimeUnit.MINUTES);

        if(wifiRequired) {
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build();
            reqBuilder.setConstraints(constraints);
        }

        PeriodicWorkRequest workRequest = reqBuilder.build();
        WorkManager workManager = WorkManager.getInstance(StudyCompanion.getAppContext());
        workManager.enqueueUniquePeriodicWork(getWorkerName(workerClass), ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest);
    }

    public static <T extends WorkerBase> void globalDeactivate(Class<T> workerClass ) {
        String logMsg = "Disabling periodic Worker '" + getWorkerName(workerClass);
        log(logMsg, true);

        WorkManager workManager = WorkManager.getInstance(StudyCompanion.getAppContext());
        workManager.cancelUniqueWork(getWorkerName(workerClass));

        getSp().edit().putBoolean(getPrefName(workerClass, PREF_ACTIVE), false).commit();
    }

    private boolean isActive() {
        return getSp().getBoolean(getPrefName(this.getClass(), PREF_ACTIVE), false);
    }

    @SuppressLint("ApplySharedPref")
    public static <T extends WorkerBase> void setGlobalIntervalMinutes(Class<T> workerClass, int intervalMinutes) {
        getSp().edit().putInt(getPrefName(workerClass, PREF_INTERVAL), intervalMinutes).commit();
    }

    public static <T extends WorkerBase> int getGlobalIntervalMinutes(Class<T> workerClass) {
        return getSp().getInt(getPrefName(workerClass, PREF_INTERVAL), DEFAULT_INTERVAL_MINUTES);
    }


    @NonNull
    @Override
    public final ListenableFuture<Result> startWork() {

        // Background tasks mostly require authenticated user, so re-init user session in case if state was lost
        if(BackendIO.getCurrentUser() == null) {
            // BackendIO's state might have been wiped or it was never initialized in current Android session.
            BackendIO.initialize(getApplicationContext()); // Current User will immediately be restored from local cache (and verified by server asynchronously)
        }

        String logMsg = "Starting work for Worker: " + getWorkerName(this.getClass());
        log(logMsg, true);

        if(currentWorkResult != null) {
            log("Tried to start new work when this worker is still working! Worker will NOT be re-enqueued! NEEDS DEBUGGING!", false);
            return currentWorkResult;
        }

        currentWorkResult = SettableFuture.create();

        if(isActive()) {

            // Check if sync work defined:
            if(_doWorkSync() != null) {
                return currentWorkResult;
            }

            currentWorkResult = SettableFuture.create();

            try {
                startWorkAsync();
            } catch (Throwable throwable) {
                finishWorkWithException(throwable);
            }

            return currentWorkResult;
        }

        // if active was true, the method has already returned.
        // If active == false:

        // Finish work immediately with positive result without doing anything, if this worker is deactivated.
        finishWork(Result.success());
        return currentWorkResult;
    }


    private Result _doWorkSync() {

         try {
             Result workRes = doWork();
            if(workRes == null) {
                // doWork() was probably not overridden
                return null;
            }
            finishWork(workRes);
            return Result.success();
        } catch (Throwable throwable) {
            finishWorkWithException(throwable);
            return Result.failure();
        }
    }

    protected final void finishWork(Result result) {
        String logMsg = "Background Worker terminated successfully.\n" +
                " Worker: " + this.getClass().getName() + "\n";
        log(logMsg, true);
        if(currentWorkResult != null) {
            currentWorkResult.set(result);
        }

    }

    protected final void finishWorkWithException(Throwable exception) {
        String logMsg = "Background Worker terminated with exception.\n" +
                " Worker: " + getWorkerName(this.getClass()) + "\n" +
                " Exception: " + exception.toString();
        log(logMsg, false);
        if(currentWorkResult != null) {
            currentWorkResult.setException(exception);
        }
    }

    private static void log(String logMsg, boolean verbose) {
        if(verbose && !VERBOSE_LOGGING) {
            return;
        }
        BackendIO.serverLog(Log.DEBUG, LOG_TAG, logMsg);
    }

    public void startWorkAsync() {     }

    public Result doWork()
    {
        return null;
    }

}
