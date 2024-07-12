//package de.thwildau.f4f.studycompanion.sensors.garmin;
//
//import android.os.Parcel;
//import android.os.Parcelable;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//
//import com.garmin.health.DeviceModel;
//import com.garmin.health.settings.DeviceSettings;
//import com.garmin.health.settings.DeviceSettingsSchema;
//import com.garmin.health.settings.DisplayScreens;
//import com.garmin.health.settings.Gender;
//import com.garmin.health.settings.Handedness;
//import com.garmin.health.settings.Language;
//import com.garmin.health.settings.PairSettingsUtil;
//import com.garmin.health.settings.Settings;
//import com.garmin.health.settings.TimeFormat;
//import com.garmin.health.settings.UnitSettings;
//import com.garmin.health.settings.UnitSystem;
//import com.garmin.health.settings.UserSettings;
//
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.text.DateFormat;
//import java.text.ParseException;
//import java.util.Calendar;
//import java.util.Date;
//import java.util.HashSet;
//import java.util.Locale;
//import java.util.Set;
//
//import de.thwildau.f4f.studycompanion.StudyCompanion;
//import de.thwildau.f4f.studycompanion.Utils;
//import de.thwildau.f4f.studycompanion.backend.BackendIO;
//import de.thwildau.f4f.studycompanion.datamodel.User;
//
//public class GarminSettingsProvider {
//
//    private static final String LOG_TAG = "UserSettingsProvider";
//
//    // User Settings:
//    //   (Default values oriented at German average person)
//    private static final int DEFAULT_AGE = 42;
//    private static final float DEFAULT_HEIGHT_M = 1.659f;
//    private static final float DEFAULT_WEIGHT_KG = 85.2f;
//    private static final String DEFAULT_GENDER = Gender.FEMALE;
//    private static final int DEFAULT_SLEEP_START = 79200; // 22:00 (in seconds after Midnight)
//    private static final int DEFAULT_SLEEP_END = 21600; // 06:00
//    private static final String DEFAULT_HANDEDNESS = Handedness.RIGHT_HANDED;
//    private static final String DEFAULT_LANGUAGE = Language.GERMAN;
//
//    // Unit Settings:
//    private static final String DEFAULT_DATE_FORMAT = com.garmin.health.settings.DateFormat.DAY_MONTH;
//    private static final String DEFAULT_TIME_FORMAT = TimeFormat.TWENTY_FOUR_HOUR;
//    private static final String DEFAULT_UNIT = UnitSystem.METRIC;
//
//    private JSONObject staticData = null;
//    private DeviceModel deviceModel = null;
//
//    GarminSettingsProvider(DeviceModel deviceModel, User user) {
//        this.deviceModel = deviceModel;
//
//        String staticDataStr = null;
//
//        if(user != null) {
//            staticDataStr = user.anamnesisData;
//        }
//
//        if(Utils.nullOrEmpty(staticDataStr)) {
//            Log.w(LOG_TAG, "Selected user has no static data. Using default values for Garmin UserSettings.");
//            return;
//        }
//
//        try {
//            staticData = new JSONObject(staticDataStr);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public int getAge() {
//        if(staticData == null || !staticData.has("birth_date")) {
//            return DEFAULT_AGE;
//        }
//
//        try {
//            // Calculate age by difference from now to birth date (rounded).
//            String val = staticData.getString("birth_date");
//            if(Utils.nullOrEmpty(val)) {
//                return  DEFAULT_AGE;
//            }
//
//            Date birthDate = Utils.getServerTimeFormat().parse(val);
//
//            Calendar calBirth = Calendar.getInstance();
//            calBirth.setTime(birthDate);
//
//            Calendar calNow = Calendar.getInstance();
//            calNow.setTime(new Date());
//
//            int age = calNow.get(Calendar.YEAR) - calBirth.get(Calendar.YEAR);
//            int monthsDiff = calNow.get(Calendar.MONTH) - calBirth.get(Calendar.MONTH);
//
//            // round to full year
//            if(monthsDiff > 6) {
//                age++;
//            }
//            else if(monthsDiff < -6) {
//                age--;
//            }
//
//            return age;
//
//        } catch (JSONException | ParseException e) {
//            e.printStackTrace();
//            return DEFAULT_AGE;
//        }
//
//
//    }
//
//    public float getHeight() {
//        if(staticData == null || !staticData.has("height")) {
//            return DEFAULT_HEIGHT_M;
//        }
//        try {
//            int sizeCm = staticData.getInt("height");
//            return sizeCm / 100f;
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return DEFAULT_HEIGHT_M;
//        }
//    }
//
//    public float getWeight() {
//        if(staticData == null || !staticData.has("weight")) {
//            return DEFAULT_WEIGHT_KG;
//        }
//        try {
//            return (float)staticData.getInt("weight");
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return DEFAULT_WEIGHT_KG;
//        }
//    }
//
//    public String getGender() {
//        if(staticData == null || !staticData.has("gender")) {
//            return DEFAULT_GENDER;
//        }
//        try {
//            String genderStr = staticData.getString("gender");
//            switch(genderStr) {
//                case "m":
//                    return Gender.MALE;
//                case "f":
//                    return Gender.FEMALE;
//                default: // other gender is not (yet) supported by Garmin
//                    return DEFAULT_GENDER;
//            }
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return DEFAULT_GENDER;
//        }
//    }
//
//    /**
//     * convert a time string read from static data in server format to number of seconds after midnight
//     * @param fieldId
//     * @param defaultValue
//     * @return
//     */
//    private int convertSleepTime(String fieldId, int defaultValue) {
//        if(staticData == null || !staticData.has(fieldId)) {
//            return defaultValue;
//        }
//
//        try {
//            String timeStr = staticData.getString(fieldId);
//
//            if(Utils.nullOrEmpty(timeStr)) {
//                return defaultValue;
//            }
//
//            DateFormat serverTimeFormat = Utils.getServerTimeFormat();
//            Date time = serverTimeFormat.parse(timeStr);
//            Calendar cal = Calendar.getInstance();
//            cal.setTime(time);
//            int secondsAfterMidnight = cal.get(Calendar.HOUR_OF_DAY) * 3600;
//            secondsAfterMidnight += cal.get(Calendar.MINUTE) * 60;
//
//            return secondsAfterMidnight;
//
//        } catch (JSONException | ParseException e) {
//            e.printStackTrace();
//            return defaultValue;
//        }
//    }
//
//    public int getSleepStart() {
//        return convertSleepTime("avg_sleep_start", DEFAULT_SLEEP_START);
//    }
//
//    public int getSleepEnd() {
//        return convertSleepTime("avg_sleep_end", DEFAULT_SLEEP_END);
//    }
//
//    public String getHandedness() {
//        if(staticData == null || !staticData.has("wearing_arm")) {
//            return DEFAULT_HANDEDNESS;
//        }
//        try {
//            String wearingArmStr = staticData.getString("wearing_arm");
//            switch(wearingArmStr) {
//                // Handedness is meant as the opposite of wearing arm
//
//                case "left":
//                    return Handedness.RIGHT_HANDED;
//                case "right":
//                    return Handedness.LEFT_HANDED;
//                default:
//                    return DEFAULT_HANDEDNESS;
//            }
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return DEFAULT_HANDEDNESS;
//        }
//    }
//
//    public String getLanguage() {
//        String deviceLanguage = Locale.getDefault().getLanguage();
//        if(deviceLanguage.equals(Locale.CHINESE.getLanguage())) {
//            return Language.CHINESE;
//        } else if(deviceLanguage.equals(Locale.ENGLISH.getLanguage())) {
//            return Language.ENGLISH;
//        } else if(deviceLanguage.equals(Locale.FRENCH.getLanguage())) {
//            return Language.FRENCH;
//        } else if(deviceLanguage.equals(Locale.GERMAN.getLanguage())) {
//            return Language.GERMAN;
//        } else if(deviceLanguage.equals(Locale.ITALIAN.getLanguage())) {
//            return Language.ITALIAN;
//        } else if(deviceLanguage.equals(Locale.JAPANESE.getLanguage())) {
//            return Language.JAPANESE;
//        } else if(deviceLanguage.equals(Locale.KOREAN.getLanguage())) {
//            return Language.KOREAN;
//        } else if(deviceLanguage.equals(new Locale("es").getLanguage())) {
//            return Language.SPANISH;
//        } else if(deviceLanguage.equals(new Locale("pt").getLanguage())) {
//            return Language.PORTUGUESE;
//        } else if(deviceLanguage.equals(new Locale("el").getLanguage())) {
//            return Language.GREEK;
//        } else if(deviceLanguage.equals(new Locale("pl").getLanguage())) {
//            return Language.POLISH;
//        } else if(deviceLanguage.equals(new Locale("cs").getLanguage())) {
//            return Language.CZECH;
//        } else if(deviceLanguage.equals(new Locale("nl").getLanguage())) {
//            return Language.DUTCH;
//        } else if(deviceLanguage.equals(new Locale("da").getLanguage())) {
//            return Language.DANISH;
//        } else if(deviceLanguage.equals(new Locale("ru").getLanguage())) {
//            return Language.RUSSIAN;
//
//        } else {
//            return DEFAULT_LANGUAGE;
//        }
//    }
//
//    public UserSettings buildUserSettings() {
//        UserSettings.Builder userSettingsBuilder = new UserSettings.Builder();
//
//        userSettingsBuilder.setDeviceLanguage(getLanguage());
//        userSettingsBuilder.setAge(getAge());
//        userSettingsBuilder.setGender(getGender());
//        userSettingsBuilder.setHandedness(getHandedness());
//        userSettingsBuilder.setHeight(getHeight());
//        userSettingsBuilder.setSleepEnd(getSleepEnd());
//        userSettingsBuilder.setSleepStart(getSleepStart());
//        userSettingsBuilder.setWeight(getWeight());
//        //TODO: Set Walking and Running Step Length, if possible
//
//        return userSettingsBuilder.build();
//    }
//
//    public DeviceSettings buildDeviceSettings() {
//        if(deviceModel == null) {
//            throw new IllegalStateException("Can only build DeviceSettings when device model is specified.");
//        }
//
//        // implementation taken from Garmin Sample App.
//
////        DeviceSettings.Builder deviceSettingsBuilder = DeviceSettings.Builder.getDefaultBuilder(deviceModel);
//                // no longer working with SDK >= 3.5.2!
//
//        // TODO: Dry implementation. Test if this works!
//        DeviceSettings deviceSettings = PairSettingsUtil.getDefaultDeviceSettings(StudyCompanion.getAppContext(), deviceModel);
//
//        if(deviceSettings == null) {
//            BackendIO.serverLog(Log.ERROR, "GarminSettingsProvider", "Could not create default Garmin Device Settings configuration. getDefaultDeviceSettings() returned null!");
//            return null;
//        }
//
//        DeviceSettings.Builder deviceSettingsBuilder = deviceSettings.edit();
//
//        try
//        {
//            if(deviceModel == DeviceModel.VIVOSMART_4)
//            {
//                Set<String> displayScreens = new HashSet<>();
//                displayScreens.add(DisplayScreens.TIME_DATE);
//                displayScreens.add(DisplayScreens.STEPS);
//                displayScreens.add(DisplayScreens.HEART_RATE);
//                deviceSettingsBuilder.setDisplayScreens(displayScreens);
//                deviceSettingsBuilder.setHeartrateEnabled(true);
//            }
//        }
//        catch(Exception ignored) {}
//
//        return deviceSettingsBuilder.build();
//    }
//
//    public UnitSettings buildUnitSettings() {
//        if(deviceModel == null) {
//            throw new IllegalStateException("Can only build UnitSettings when device model is specified.");
//        }
//
////        UnitSettings.Builder unitSettingsBuilder = UnitSettings.Builder.getDefaultBuilder(deviceModel);
//                    // no longer working with SDK >= 3.5.2!
//
//        // TODO: Dry implementation. Test if this works!
//        UnitSettings unitSettings = PairSettingsUtil.getDefaultUnitSettings(StudyCompanion.getAppContext(), deviceModel);
//
//        if(unitSettings == null){
//            BackendIO.serverLog(Log.ERROR, "GarminSettingsProvider", "Could not create default Garmin Unit Settings configuration. getDefaultUnitSettings() returned null!");
//            return null;
//        }
//
//        UnitSettings.Builder unitSettingsBuilder = unitSettings.edit();
//
//
//        //TODO: Build Unit Settings based on Android device configuration
//
//        unitSettingsBuilder.setDateFormat(DEFAULT_DATE_FORMAT);
//        unitSettingsBuilder.setTimeFormat(DEFAULT_TIME_FORMAT);
//        unitSettingsBuilder.setUnitSystem(DEFAULT_UNIT);
//
//
//
//        return unitSettingsBuilder.build();
//    }
//
//
//    public void setStaticData(JSONObject staticData) {
//        this.staticData = staticData;
//    }
//
//    public void setDeviceModel(DeviceModel deviceModel) {
//        this.deviceModel = deviceModel;
//    }
//}
