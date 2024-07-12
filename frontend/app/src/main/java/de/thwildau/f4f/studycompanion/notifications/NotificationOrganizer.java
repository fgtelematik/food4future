package de.thwildau.f4f.studycompanion.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import de.thwildau.f4f.studycompanion.MainActivity;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;

public class NotificationOrganizer {
    private static final int NOTIFICATION_TIMEOUT = 60; // In Seconds.
        // A timeout is used to avoid having a irremovable sync notification stuck in the UI when a sync process
        // was not properly terminated. This can happen for example when the connection to a Garmin sensor
        // was lost during an ongoing sync process.

    private static final String SYNC_CHANNEL_ID = "CHANNEL_SYNC";
    private static final String NUDGING_CHANNEL_ID = "NUDGING_CHANNEL_SYNC";

    public static final String EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID";

    public static final int NOTIFICATION_ID_COSINUSS_CONNECTED = 20;
    public static final int NOTIFICATION_ID_COSINUSS_REMINDER = 21;
    public static final int NOTIFICATION_ID_USER_INPUT_REMINDER = 22;
    public static final int NOTIFICATION_ID_UPDATE_AVAILABLE = 23;
    public static final int NOTIFICATION_ID_DOWNLOADING_APK = 24;
    public static final int NOTIFICATION_ID_COSINUSS_FINISHED = 25;


    private static Handler delayHandler;
    private static HashMap<Integer, Runnable> delayTasks;

    public enum SyncType {
        ServerSync(10),
        SensorSync(11),
        APKDownload(12);

        private final int notificationId;
        SyncType(int id) {
            notificationId = id;
        }

        public int getNotificationId() {
            return notificationId;
        }
    }

    public static void initialize() {
        createNotificationChannels();
    }



    public static void showSyncNotificationWithProgress(SyncType syncType, int progress) {
        Notification notification = createSyncNotificationWithProgress(syncType, progress);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(syncType.getNotificationId(), notification);
        resetSyncNotificationTimeout(syncType.getNotificationId());
    }

    public static Notification createSyncNotificationWithProgress(SyncType syncType, int progress) {
        NotificationCompat.Builder builder = createNotificationBuilder(syncType);
        if(builder == null) {
            return null;
        }

        if(progress >= 0) {
            builder.setProgress(100, progress, false);
        }

        Notification notification = builder.build();

        return notification;
    }

    public static void showSyncNotification(SyncType syncType ) {
        showSyncNotificationWithProgress(syncType, -1);
    }

    private static void hideNotification(int notificationId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.cancel(notificationId);
    }

    public static void hideSyncNotification(SyncType syncType) {
        hideNotification(syncType.getNotificationId());
    }

    private static void createNotificationChannels() {
        // Create the NotificationChannels, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);

        if(notificationManager == null) {
            return;
        }

        // Create Sync Channel
        CharSequence name = getContext().getString(R.string.notification_channel_sync_name);
        String description = getContext().getString(R.string.notification_channel_sync_description);
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel syncChannel = new NotificationChannel(SYNC_CHANNEL_ID, name, importance);
        syncChannel.setDescription(description);

        // Create Nudging Channel
        name = getContext().getString(R.string.notification_channel_nudging_name);
        description = getContext().getString(R.string.notification_channel_nudging_description);
        importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel nudgingChannel = new NotificationChannel(NUDGING_CHANNEL_ID, name, importance);
        nudgingChannel.setDescription(description);

        // Register Channels. This is only done the very first time the app is executed.
        // After that, the following lines will be no-ops.
        notificationManager.createNotificationChannel(syncChannel);
        notificationManager.createNotificationChannel(nudgingChannel);
    }

    private static Context getContext() {
        return StudyCompanion.getAppContext();
    }


    private static NotificationCompat.Builder createNotificationBuilder(SyncType syncType) {
        int textId = 0;
        switch(syncType) {
            case ServerSync:
                textId = R.string.nofitication_message_sync_server;
                break;
            case SensorSync:
                textId = R.string.notification_message_sync_sensor;
                break;
            case APKDownload:
                textId = R.string.notification_message_sync_apk_download;
                break;
        }

        Intent i = new Intent(getContext(), MainActivity.class);
        i.putExtra(EXTRA_NOTIFICATION_ID, syncType.getNotificationId());
        int immutableFlag = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(getContext(), syncType.getNotificationId(), i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag );

        return new NotificationCompat.Builder(getContext(), SYNC_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_f4f_logo_foreground)
                .setContentTitle(getContext().getString(textId))
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOngoing(true)
                .setContentIntent(pi)
                .setProgress(100,50, true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private static void resetSyncNotificationTimeout(int notificationId) {
        int timeoutMs = NOTIFICATION_TIMEOUT * 1000;
        if(delayHandler == null) {
            delayHandler = new Handler(Looper.getMainLooper());
        }

        if(delayTasks == null) {
            delayTasks = new HashMap<>();
        }

        Runnable delayTask = delayTasks.get(notificationId);

        if(delayTask == null) {
            delayTask = () -> hideNotification(notificationId);
            delayTasks.put(notificationId, delayTask);
        }

        delayHandler.removeCallbacks(delayTask);
        delayHandler.postDelayed(delayTask, timeoutMs);
    }

    public static void showCosinussNotification(Integer heartRate, Float temperature,  Integer signalQuality, long connectedTimeMs ) {

        Context context = getContext();
        StringBuffer infotext = new StringBuffer();
        String title = context.getString(R.string.notification_title_cosinuss_connected);
        String lineBreak = "";

        if(heartRate != null) {
            infotext.append(Utils.getText(context, R.string.sensor_heart_rate, heartRate));
            lineBreak = "\n";
        }
        if(temperature != null) {
            infotext.append(lineBreak);
            infotext.append(Utils.getText(context, R.string.sensor_temperature, temperature));
            lineBreak = "\n";
        }
        if(signalQuality != null) {
            signalQuality = StudyCompanion.getCosinussSensorManager().convertPositioningQuality(signalQuality);
            int signalQualityLabelRes = R.string.sensor_positioning_label_bad;

            if(signalQuality >= 50)
                signalQualityLabelRes = R.string.sensor_positioning_label_medium;

            if(signalQuality == 100)
                signalQualityLabelRes = R.string.sensor_positioning_label_good;


            String positioning_label = context.getString(signalQualityLabelRes);

            infotext.append(lineBreak);
            infotext.append(Utils.getText(context, R.string.sensor_positioning_label, positioning_label));
        }

        if(connectedTimeMs > 0){
            long diffSeconds = (System.currentTimeMillis() - connectedTimeMs) / 1000;
            infotext.append(lineBreak);
            infotext.append(Utils.getText(context, R.string.sensor_connection_time, diffSeconds / 60, diffSeconds % 60));
        }

        Intent i = new Intent(getContext(), MainActivity.class);
        i.putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_COSINUSS_CONNECTED);
        int immutableFlag = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(getContext(),NOTIFICATION_ID_COSINUSS_CONNECTED, i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag );

        NotificationCompat.BigTextStyle bts = new NotificationCompat.BigTextStyle().setBigContentTitle(title);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), SYNC_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_f4f_logo_foreground)
                .setContentTitle(title)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if(!Utils.nullOrEmpty(infotext)) {
            builder.setContentText(infotext);
            bts.bigText(infotext);
        }

        builder.setStyle(bts);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(NOTIFICATION_ID_COSINUSS_CONNECTED, builder.build());

        resetSyncNotificationTimeout(NOTIFICATION_ID_COSINUSS_CONNECTED);
    }

    public static void hideCosinussNotification() {
        hideNotification(NOTIFICATION_ID_COSINUSS_CONNECTED);
    }


    public static void showCosinussReminder() {
        int sensorDuration = 30;
        String sensorDurationStr = SchemaProvider.getDeviceConfig().getCosinussWearingTimeDuration();
        if(!Utils.nullOrEmpty(sensorDurationStr)) {
            sensorDuration = Utils.getMinutesFromMilitaryTimeDuration(sensorDurationStr);
        }
        String startTime = "XX:XX";
        Date startTimeDate = Utils.todayTimeFromMilitaryTime(SchemaProvider.getDeviceConfig().getCosinussWearingTimeBegin());
        if(startTimeDate != null) {
            startTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(startTimeDate);
        }

        String title = getContext().getString(R.string.notification_title_cosinuss_reminder);
        String message = getContext().getString(R.string.notification_text_cosinuss_reminder, startTime, sensorDuration);

        showReminderNotification(NOTIFICATION_ID_COSINUSS_REMINDER, title, message);
    }

    public static void showUserInputReminder() {
        String title = getContext().getString(R.string.notification_title_user_input_reminder);
        String message = getContext().getString(R.string.notification_text_user_input_reminder);

        showReminderNotification(NOTIFICATION_ID_USER_INPUT_REMINDER, title, message);
    }

    public static void showAppUpdateReminder() {
        String title = getContext().getString(R.string.notification_title_app_update_reminder);
        String message = getContext().getString(R.string.notification_text_app_update_reminder);

        showReminderNotification(NOTIFICATION_ID_UPDATE_AVAILABLE, title, message);
    }

    public static void showCosinussWearingCompletedReminder() {
        String title = getContext().getString(R.string.notification_title_cosinuss_wearing_completed);
        String message = getContext().getString(R.string.notification_text_cosinuss_wearing_completed);

        showReminderNotification(NOTIFICATION_ID_COSINUSS_FINISHED, title, message);
    }


    private static void showReminderNotification(int notificationId, String title, String message) {

        Intent i = new Intent(getContext(), MainActivity.class);
        i.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        PendingIntent pi = null;

        int immutableFlag = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ? PendingIntent.FLAG_IMMUTABLE : 0;
        pi = PendingIntent.getActivity(getContext(),notificationId, i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag);


        NotificationCompat.BigTextStyle bts = new NotificationCompat.BigTextStyle().setBigContentTitle(title).setSummaryText(message);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), NUDGING_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_f4f_logo_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setOngoing(false)
                .setContentIntent(pi)
                .setStyle(bts);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        Notification notification;
        notification = builder.build();

        notificationManager.notify(notificationId, notification);
    }

    public static void hideCosinussReminder() {
        hideNotification(NOTIFICATION_ID_COSINUSS_REMINDER);
    }

    public static void hideCosinussWearingCompletedReminder() {
        hideNotification(NOTIFICATION_ID_COSINUSS_FINISHED);
    }

}
