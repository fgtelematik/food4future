package de.thwildau.f4f.studycompanion;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import de.thwildau.f4f.studycompanion.sensors.SensorManagerBase;
import de.thwildau.f4f.studycompanion.sensors.cosinuss.CosinussSensorManager;
//import de.thwildau.f4f.studycompanion.sensors.garmin.GarminSensorManager;
import io.realm.Realm;
import io.realm.RealmConfiguration;

public class StudyCompanion extends Application {
    private static final String LOG_TAG = "StudyCompanionApp";
//    private static GarminSensorManager sensorManager;
    private static CosinussSensorManager cosinussSensorManager;
    private final static List<SensorManagerBase> sensorManagers = new ArrayList<>();
    private static Application mInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;

        Realm.init(getAppContext());

//        Stetho.initialize(
//                Stetho.newInitializerBuilder(this)
//                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
//                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
//                        .build());

        RealmConfiguration realmConfig =
                new RealmConfiguration.Builder()
                        .schemaVersion(1)
                        .deleteRealmIfMigrationNeeded()
                        .allowWritesOnUiThread(true)
                        .build();

        Realm.setDefaultConfiguration(realmConfig);

        // Init Notification management
        NotificationOrganizer.initialize();

        BackendIO.initialize(getApplicationContext());

//        sensorManager = new GarminSensorManager();
        cosinussSensorManager = new CosinussSensorManager();

//        // Init Garmin Manager
//        sensorManager.init();
//        sensorManager.start();

        // Init Cosinuss Manager
        cosinussSensorManager.init();
        cosinussSensorManager.start();

//        sensorManagers.add(sensorManager);
        sensorManagers.add(cosinussSensorManager);

        BackendIO.serverLog(Log.INFO, LOG_TAG, "StudyCompanion app created.");
    }

    public static Context getAppContext() {
        return mInstance.getApplicationContext();
    }

//    public static GarminSensorManager getGarminSensorManager() {
//        return sensorManager;
//    }

    public static void updateSensorConfig() {
        for (SensorManagerBase manager:
             sensorManagers) {
            manager.updateConfig();
        }
    }

    public static CosinussSensorManager getCosinussSensorManager() {
        return cosinussSensorManager;
    }

    public static SharedPreferences getGlobalPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getAppContext());
    }

    public static SharedPreferences getUserPreferences() {
        User user = BackendIO.getCurrentUser();
        return getUserPreferences(user);
    }

    public static SharedPreferences getUserPreferences(User user) {
        if (user == null) {
            return null;
        }

        return getAppContext().getSharedPreferences("user_" + user.id, MODE_PRIVATE);
    }

    public static List<SensorManagerBase> getSensorManagers() {
        return sensorManagers;
    }

    public static boolean isReleaseBuild() {
//        return true;  // Uncomment for simulating release build
        return "release".equals(BuildConfig.BUILD_TYPE);
    }

}
