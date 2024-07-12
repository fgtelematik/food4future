package de.thwildau.f4f.studycompanion.backend;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import de.thwildau.f4f.studycompanion.BuildConfig;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import io.reactivex.internal.schedulers.NewThreadWorker;

public class AppUpdater {

    public enum ApkUpdateState {
        IDLE, // this state ist only set on initialization before tryDownloadNewApk() was called the first time..
        DOWNLOADING,
        FINISH_SUCCESS_OR_NO_DOWNLOAD_NEEDED,
        FINISH_ERROR
    }

    private final static String LOG_TAG = "AppUpdater";
    private final static String CACHED_APK_FILENAME = "f4fstudycompanion_update.apk";
    private final static String EXTERNAL_FILES_DIRECTORY = "external";
    private final static Utils.ObservableValue<Boolean> observableUpdatedApkReadyToInstallState = new Utils.ObservableValue<>(false);
    private final static Utils.ObservableValue<ApkUpdateState> observableApkDownloadState = new Utils.ObservableValue<>(ApkUpdateState.IDLE);
    private final static Utils.ObservableValue<Integer> observableApkDownloadProgress = new Utils.ObservableValue<>(null);

    public static void tryDownloadNewApk() {

        if(observableApkDownloadState.getValue() == ApkUpdateState.DOWNLOADING) {
            // already downloading
            // (potential race condition, when this method was simultaneously called from BackgroundWorker and MainActivity,
            // but this is sooo unlikely, that I will keep it as it is for now)
            return;
        }

        final File apkFile = new File(getCachedApkPath());
        final Uri apkDownloadUri = SchemaProvider.getDeviceConfig().getApkDownloadUrl();

        if(apkDownloadUri == null) {
            // even if there was a new App version available, we couldn't download it
            // since the apk download URL is not specified
            observableApkDownloadState.setValue(ApkUpdateState.FINISH_ERROR);
            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Error on checking App Update state. Could not obtain APK Download URL.");
            return;
        }

        boolean isDownloadNeeded = shouldDownloadAPK();
        if(!isDownloadNeeded) {
            observableApkDownloadState.setValue(ApkUpdateState.FINISH_SUCCESS_OR_NO_DOWNLOAD_NEEDED);
            return;
        }

        // -- NEW APP VERSION IS AVAILABLE AND SHOULD BE DOWNLOADED --

        // Delete current cached version
        if(apkFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            apkFile.delete();
        }
        updateCachedVersionPreference(null);

        BackendIO.serverLog(Log.INFO, LOG_TAG, "Downloading APK versionCode " + SchemaProvider.getDeviceConfig().getServerApkVersionCode() +  " from Server...");

        observableApkDownloadState.setValue(ApkUpdateState.DOWNLOADING);


        // Start downloading APK file in a separate Thread
        try {
            new Thread(() -> {
                try {
                    URL url = new URL(apkDownloadUri.toString());

                    String targetPath = getCachedApkPath();
                    String targetPathTmp = targetPath + ".tmp";

                    File targetFile = new File(targetPath);
                    if(targetFile.exists()) {
                        targetFile.delete();
                    }

                    File targetFileTmp = new File(targetPathTmp);
                    if(targetFileTmp.exists()) {
                        targetFileTmp.delete();
                    }

                    URLConnection connection = url.openConnection();
                    long filesize = connection.getContentLengthLong();

                    InputStream in = new BufferedInputStream(connection.getInputStream(), 8192);
                    OutputStream out = new FileOutputStream(targetPathTmp);

                    byte[] data = new byte[1024];
                    int count;
                    long total = 0;
                    int progressPercent = -1;

                    observableApkDownloadProgress.setValue(0);


                    // Reading data from remote source and write to local file
                    while ((count = in.read(data)) != -1) {
                        out.write(data, 0, count);
                        total +=count;
                        int newProgressPercent = Math.round(100f * total / filesize );
                        if(newProgressPercent != progressPercent) {
                            // only notifiy when percentage has incremented,
                            // otherwise setForegroundAsync in BackendSyncWorker would slow down the download process.
                            progressPercent = newProgressPercent;
                            observableApkDownloadProgress.setValue(progressPercent);
                        }
                    }

                    out.flush();

                    in.close();
                    out.close();

                    targetFileTmp.renameTo(targetFile);


                    observableApkDownloadProgress.setValue(100);

                    BackendIO.serverLog(Log.INFO, LOG_TAG, "APK Download finished successfully. Received " + total + " Bytes.");

                    updateCachedVersionPreference(SchemaProvider.getDeviceConfig().getServerApkVersionCode());
                    observableApkDownloadState.setValue(ApkUpdateState.FINISH_SUCCESS_OR_NO_DOWNLOAD_NEEDED);
                    observableUpdatedApkReadyToInstallState.setValue(true);

                    // show update reminder notification to user
                    NotificationOrganizer.showAppUpdateReminder();

                } catch (Exception e) {
                    BackendIO.serverLog(Log.ERROR, LOG_TAG,  "Error on downloading new APK file from server: " + e);
                    e.printStackTrace();
                    observableApkDownloadState.setValue(ApkUpdateState.FINISH_ERROR);
                }
            }).start();
        } catch(Throwable t) {
            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Error: Thread for APK download could not be started.");
            observableApkDownloadState.setValue(ApkUpdateState.FINISH_ERROR);
        }
    }

    public static void installUpdatedApk(Activity contextActivity) {
        if(!isUpdatedApkReadyToInstall()) {
            Toast.makeText(contextActivity, contextActivity.getString(R.string.app_updater_no_apk_found), Toast.LENGTH_LONG).show();
            observableUpdatedApkReadyToInstallState.setValue(false);
            return;
        }

        String filename = getCachedApkPath();
        Uri uri = FileProvider.getUriForFile(contextActivity, BuildConfig.APPLICATION_ID + ".provider", new File(filename));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        contextActivity.startActivity(intent);
    }


    public static boolean checkOrObtainPackageInstallPermission(Fragment contextFragment, int requestCode, boolean obtainIfNotGranted) {
        if(Build.VERSION.SDK_INT < 26) {
            return true; // No explicit permission needed for Android 7.x and lower
        }

        if(contextFragment.getActivity().getPackageManager().canRequestPackageInstalls()) {
            return true; // Permission already granted
        }

        if(obtainIfNotGranted) {
            new AlertDialog.Builder(contextFragment.getActivity())
                    .setTitle(R.string.app_updater_permission_dialog_title)
                    .setMessage(R.string.app_updater_permission_dialog_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        contextFragment.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + BuildConfig.APPLICATION_ID)), requestCode);
                    })
                    .show();
        }
        return false;
    }



    public static Utils.ObservableValue<Boolean> getUpdatedApkReadyToInstallState() {
        boolean currentState = observableUpdatedApkReadyToInstallState.getValue();
        boolean newState = isUpdatedApkReadyToInstall();
        if(newState != currentState) {
            observableUpdatedApkReadyToInstallState.setValue(newState);
        }

        return observableUpdatedApkReadyToInstallState;
    }

    public static Utils.ObservableValue<ApkUpdateState> getApkDownloadState() {
        return observableApkDownloadState;
    }

    public static Utils.ObservableValue<Integer> getObservableApkDownloadProgress() {
        return observableApkDownloadProgress;
    }

    public static void showUpdateReleaseNotesIfNecessary(Activity contextActivity, boolean forceShow) {
        
        String prefLastVersion = contextActivity.getString(R.string.lastUsedAPKversionCode);
        String prefLastServerSyncTime = contextActivity.getString(R.string.lastServerSyncTime);
        String prefKeepShowingReleaseNotes = contextActivity.getString(R.string.keepShowingReleaseNotes);

        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        Integer lastAppVersion = sp.contains(prefLastVersion) ? sp.getInt(prefLastVersion, BuildConfig.VERSION_CODE) : null;
        boolean keepShowingReleaseNotes = sp.getBoolean(prefKeepShowingReleaseNotes, true);

        boolean updateDone = false;

        if(lastAppVersion != null && lastAppVersion < BuildConfig.VERSION_CODE) {
            updateDone = true;
        }

        if(lastAppVersion == null || lastAppVersion != BuildConfig.VERSION_CODE) {
            sp.edit().putInt(prefLastVersion, BuildConfig.VERSION_CODE).apply();

            SharedPreferences spUser = StudyCompanion.getUserPreferences();
            if(spUser != null) {
                spUser.edit()
                        .putLong(prefLastServerSyncTime, 0) // make sure to download all user data on next sync, because Realm might have been wiped in new App version
                        .apply();
            }
        }



        if(updateDone || keepShowingReleaseNotes || forceShow) {
            View releaseNotesDialogView = contextActivity.getLayoutInflater().inflate(R.layout.dialog_release_notes, null);
            CheckBox checkboxHideNotes = releaseNotesDialogView.findViewById(R.id.check_hide_release_notes);
            TextView releaseNotesText = releaseNotesDialogView.findViewById(R.id.text_release_notes);
            releaseNotesText.setText(getReleaseNotesText(contextActivity));

            checkboxHideNotes.setChecked(true);

            new AlertDialog.Builder(contextActivity)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                            sp.edit().putBoolean(prefKeepShowingReleaseNotes, !checkboxHideNotes.isChecked()).apply();
                    })
                    .setCancelable(false)
                    .setView(releaseNotesDialogView)
                    .setTitle(contextActivity.getString(R.string.release_notes_title, BuildConfig.VERSION_NAME))
                    .setMessage(R.string.release_notes_message)
                    .show();
        }
    }

    private static String  getReleaseNotesText(Context context) {
        int i = -1;
        for (int rn_index: context.getResources().getIntArray(R.array.release_notes_version_index)) {
            i++;
            if (rn_index == BuildConfig.VERSION_CODE)
                break;
        }
        return i >=0 ? context.getResources().getStringArray(R.array.release_notes)[i] : "(release notes missing)";
    }

    public static boolean showUpdateReleaseNotesIfNecessary2(Activity contextActivity) {
        Context context = StudyCompanion.getAppContext();
        String prefLastVersion = context.getString(R.string.lastUsedAPKversionCode);
        String prefLastServerSyncTime = context.getString(R.string.lastServerSyncTime);

        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        Integer lastAppVersion = sp.contains(prefLastVersion) ? sp.getInt(prefLastVersion, BuildConfig.VERSION_CODE) : null;

        boolean res = false;

        if(lastAppVersion != null && lastAppVersion < BuildConfig.VERSION_CODE) {
            res = true;
        }

        if(lastAppVersion == null || lastAppVersion != BuildConfig.VERSION_CODE) {
            sp.edit().putInt(prefLastVersion, BuildConfig.VERSION_CODE).apply();

            SharedPreferences spUser = StudyCompanion.getUserPreferences();
            if(spUser != null) {
                spUser.edit()
                        .putLong(prefLastServerSyncTime, 0) // make sure to download all user data on next sync, because Realm might have been wiped in new App version
                        .apply();
            }
        }



        return res;
    }


    private static boolean isUpdatedApkReadyToInstall() {
        Context context = StudyCompanion.getAppContext();

        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        String prefKey = context.getResources().getString(R.string.cachedAPKversionCode);
        int currentApkVersion;

        currentApkVersion = BuildConfig.VERSION_CODE;

        File apkFile = new File(getCachedApkPath());

        return sp.contains(prefKey) && sp.getInt(prefKey, 0) > currentApkVersion && apkFile.exists();
    }

    private static boolean shouldDownloadAPK() {
        int currentApkVersion;
        Integer remoteApkVersion;
        Integer cachedApkVersion = null;

        Context context = StudyCompanion.getAppContext();
        File apkFile = new File(getCachedApkPath());

        // obtain current, remote and cached APK version codes:

        remoteApkVersion = SchemaProvider.getDeviceConfig().getServerApkVersionCode();

        if(remoteApkVersion == null) {
            // remote app version info is currently not available, so we will not download anything
            return false;
        }

        currentApkVersion = BuildConfig.VERSION_CODE;

        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        String prefKey = context.getResources().getString(R.string.cachedAPKversionCode);

        if(sp.contains(prefKey) && apkFile.exists()) {
            cachedApkVersion = sp.getInt(prefKey, 0);
        }

        if(remoteApkVersion > currentApkVersion) {
            if(remoteApkVersion.equals(cachedApkVersion)) {
                // new App version available, but already downloaded to cache

                // use this opportunity to refresh update reminder notification
                NotificationOrganizer.showAppUpdateReminder();

                BackendIO.serverLog(Log.INFO, LOG_TAG, "New APK version available, but not yet installed.");

                return false;
            }

            // a new version is available, which was not downloaded yet.
            return true;
        }

        // current App version is up-to-date
        return false;
    }

    private static void updateCachedVersionPreference(Integer newCachedVersion) {
        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        String prefKey = StudyCompanion.getAppContext().getResources().getString(R.string.cachedAPKversionCode);

        SharedPreferences.Editor spEd = sp.edit();

        if(newCachedVersion == null) {
            if(sp.contains(prefKey)) {
                spEd.remove(prefKey);
            }
        } else {
            spEd.putInt(prefKey, newCachedVersion);
        }

        spEd.apply();
    }

    private static String getCachedApkPath() {

        // The path were the APK is stored must be accessible from outside the App,
        // since we need a System app to take care of its installation

        Context context = StudyCompanion.getAppContext();
        String folderPath = context.getFilesDir().toString() + "/" + EXTERNAL_FILES_DIRECTORY;

        // create external files Directory if it does not exist
        File externalFilesDir = new File(folderPath);
        if(!externalFilesDir.exists()) {
            externalFilesDir.mkdirs();
        }

        return folderPath + "/" + CACHED_APK_FILENAME;
    }
}
