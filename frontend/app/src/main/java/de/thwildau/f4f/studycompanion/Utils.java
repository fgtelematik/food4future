package de.thwildau.f4f.studycompanion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.text.Html;
import android.text.SpannedString;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.datamodel.enums.Permission;
import de.thwildau.f4f.studycompanion.ui.customform.CustomField;
import io.realm.Realm;

public class Utils {

    public enum UserInputState {
        NO_DATA,
        INCOMPLETE_DATA,
        COMPLETE_DATA
    }

    /**
     * Determine, if the passed date is within the currently logged-in participant's
     * study period.
     * If there is no study period specified for current user, or if there is currently
     * no user signed in, false is returned.
     * @return
     */
    public static boolean isInStudyPeriod(Date datetime) {
        // Read study begin date from user data, if available
        User currentUser = BackendIO.getCurrentUser();
        if(currentUser == null || nullOrEmpty(currentUser.anamnesisData)) {
            return false;
        }

        Date studyBegin;
        Date studyEnd;

        try {
            JSONObject anamnesisData = new JSONObject(currentUser.anamnesisData);
            String studyBeginDateStr = anamnesisData.getString("study_begin_date");
            String studyEndDateStr = anamnesisData.getString("study_end_date");
            studyBegin = setTimeToZero(getServerTimeFormat().parse(studyBeginDateStr));
            studyEnd = setTimeToNextMidnight(getServerTimeFormat().parse(studyEndDateStr));
        } catch (JSONException | ParseException e) {
            // not available or invalid study begin date
            // in this case, displayPeriodDays or displayDayOne fields will be ignored
            // and fields are always displayed.
            return false;
        }

        long checkTime = datetime.getTime();
        return checkTime >= studyBegin.getTime() && checkTime < studyEnd.getTime();
    }

    /**
     * Returns the difference in days between a specified date
     * and the "study_begin_date" of the currently signed on user.
     * If any of these fields does not exist or cannot be interpreted or if the effective day is before
     * the begin of the study, null is returned.
     * @param effectiveDay The Date to calculate the offset for. The time part of the Date object is ignored.
     * @return
     */
    public static Integer determineCurrentOffsetDay(Date effectiveDay) {
        // Read study begin date from user data, if available
        User currentUser = BackendIO.getCurrentUser();
        if(currentUser == null || nullOrEmpty(currentUser.anamnesisData)) {
            return null;
        }

        Date studyBeginDate;

        try {
            JSONObject anamnesisData = new JSONObject(currentUser.anamnesisData);
            String studyBeginDateStr = anamnesisData.getString("study_begin_date");
            studyBeginDate = getServerTimeFormat().parse(studyBeginDateStr);
        } catch (JSONException | ParseException e) {
            // not available or invalid study begin date
            // in this case, displayPeriodDays or displayDayOne fields will be ignored
            // and fields are always displayed.
            return null;
        }

        int offsetDay = getDifferenceDays(studyBeginDate, effectiveDay);

        if(offsetDay < 0) {
            return null; // effective day is BEFORE actual start of study
        }

        return offsetDay;
    }

    /**
     *
     * @return true, if the current study supports daily questions at all, false otherwise.
     */
    public static boolean existQuestions() {
        return existQuestions(null);
    }

    /**
     *
     * @param effectiveDay The Date of the day to consider. Time part is ignored.
     *                     If null, daily constrains won't be checked, instead we check
     *                     if daily questions exist at all for this study.
     * @return true, if there is at least one question for a day or in general,
     *      * that the user can answer not considering the food list.
     */
    public static boolean existQuestions(@Nullable Date effectiveDay) {
        Integer currentOffsetDay = -1;

        if(effectiveDay != null)
            currentOffsetDay = determineCurrentOffsetDay(effectiveDay);

        if(effectiveDay != null && currentOffsetDay == null) {
            return false;
        }

        List<String> fields = SchemaProvider.getADTFields("user_data");
        List<String> nonQuestionFields = Arrays.asList("effective_day", "foodlist");

        for(String field : fields) {
            if(nonQuestionFields.contains(field)) {
                continue;
            }

            JSONObject fieldSchema = SchemaProvider.getSchemaForField(field);
            if(fieldSchema == null) {
                continue;
            }

            boolean participantCanEdit = false;

            try {
                participantCanEdit = SchemaProvider.checkPermissionForField(fieldSchema, Permission.Edit);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            boolean hasDailyConstraint = false;
                // true, if this field is visible on other days, but not on this one or always false
                // if we do not consider the date
            if(currentOffsetDay >= 0) {
                hasDailyConstraint = !SchemaProvider.checkDisplayConstraint(currentOffsetDay, fieldSchema);
            }

            if(!participantCanEdit || hasDailyConstraint) {
                continue;
            }

            return true;
        }

        return false;
    }

    /**
     * Checks, if user input data for one day contains at least one field more than the reserved fields
     * (incl. food list).  This indicates that the question form was at least opened and saved once,
     * implying that the participant answered the questions (even though he/she might left all
     * questions unanswered, which might be done consciously).
     * @param dayData The user input data JSON object for a specific daty
     * @return  UserInputState.COMPLETE_DATA if the form was opened, UserInputState.NO_DATA otherwise.
     *          UserInputState.INCOMPLETE_DATA is currently not supported by this function!
     */
    public static UserInputState getUserInputState(JSONObject dayData) {
        final List<String> reservedFieldIds =  Arrays.asList("id", "effective_day", "foodlist", "modification_time", "creation_time");

        if(dayData == null || dayData.length() == 0 || !dayData.has("effective_day")) {
            return UserInputState.NO_DATA;
        }
        boolean hasData = false;

        for (Iterator<String> it = dayData.keys(); it.hasNext(); ) {
            String fieldId = it.next();

            if(reservedFieldIds.contains(fieldId))
                continue;

            hasData = true;
            break;
        }

        return hasData ? UserInputState.COMPLETE_DATA : UserInputState.NO_DATA;

    }

    /**
     * The old version of getUserInputState().
     *
     * This method does a more thoroughful check of the different field inputs,
     * but had to be replaced by a new variant, since it does not support forms, which consist
     * only of optional fields (would always return COMPLETE_DATA then).
     * @param dayData
     * @return
     */
    public static UserInputState getUserInputStateOld(JSONObject dayData) {
        if(dayData == null || dayData.length() == 0 || !dayData.has("effective_day")) {
            return UserInputState.NO_DATA;
        }

        Date day;
        try {
            day = setTimeToZero(getServerTimeFormat().parse(dayData.getString("effective_day")));
        } catch(JSONException | ParseException e) {
            return UserInputState.NO_DATA;
        }

        List<String> fields = SchemaProvider.getADTFields("user_data");
        boolean dataComplete = true;

        for(String field : fields) {
            JSONObject fieldSchema = SchemaProvider.getSchemaForField(field);
            if(fieldSchema == null) {
                continue;
            }

            boolean participantCanEdit = false;

            try {
                participantCanEdit = SchemaProvider.checkPermissionForField(fieldSchema, Permission.Edit);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Integer currentOffsetDay = determineCurrentOffsetDay(day);

            if(!participantCanEdit || !SchemaProvider.checkDisplayConstraint(currentOffsetDay, fieldSchema)) {
                // Participant is either generally not allowed to edit this field
                // or the field has a display constraint and is not visible on this effective day
                continue;
            }

            if(!fieldSchema.optBoolean("maybeNull")) {
                if((!dayData.has(field) || dayData.isNull(field)) && !CustomField.FieldType.FoodList.toString().equals(dayData.optString("datatype"))) {
                    dataComplete = false;
                    break;
                }
            }
        }

        return dataComplete ? UserInputState.COMPLETE_DATA : UserInputState.INCOMPLETE_DATA;
    }

    public interface FragmentWrapperActivityCallback {
        void onCustomFormResult(boolean cancelled, @Nullable String updatedDataJson);
    }


    public static class SimpleDateFormatExt extends SimpleDateFormat {
        public SimpleDateFormatExt(String pattern) {
            super(pattern);
        }


        private Date unparseableDate(String source) {
            // In fact we should return null, but to avoid app crashes we return the current date on unparsable date strings.

            // This problem led to many crashes during app development, so we send a log message to server,
            // to inform that this problem still occurs.
            BackendIO.serverLog(Log.WARN, "Utils", "Unparseable Date Error: " + source + "\n" + Arrays.toString(Thread.currentThread().getStackTrace()));
            return new Date();
        }




        @Nullable
        @Override
        public Date parse(@NonNull String source) throws ParseException {
            return parse(source, false);
        }


        @Nullable
        public Date parse(@NonNull String source, boolean throwParseException) throws ParseException {

            if(source.length() < 19) {
                // input to short for being a valid UTC time string. Must at least have the format: 2021-01-01T00:00:00 ( 19 chars)
                if(throwParseException) {
                    throw new ParseException("Input too short for being a valid UTC time string. Must at least have the format: 2021-01-01T00:00:00 ( 19 chars)", source.length() - 1);
                }
                return unparseableDate(source);
            }

            String timezoneSeparator = source.substring(source.length() - 6, source.length() - 5);


            if(!timezoneSeparator.equals("+") && !timezoneSeparator.equals("-")) {
                // no timezone separator found at expected position
                source += "+00:00"; // add timezone information if missing
            }

            if(!source.contains(".")) {
                // no milliseconds or microseconds part found, so we add milliseconds.
                source = source.substring(0, 19) + ".000" + source.substring(19);
            }

            try {
                return super.parse(source);
            } catch(ParseException e) {
                // Server might return 6-digits-microseconds, but SimpleDateFormat only parses milliseconds.
                // As a workaround, we cut off the last three digitis of the microseconds part,
                // since we don't need this fine grain anyways.

                // Hence we expect a timestamp with 32 characters (example: 2021-04-01T00:00:00.000000+00:00)
                // Otherwise, we will not be able to parse the string.
                if(source.length() != 32) {
                    if(throwParseException) {
                        throw e;
                    }
                    return unparseableDate(source);
                }

                String modSource = source.substring(0,23) + source.substring(26);
                try {
                    return super.parse(modSource);
                } catch(ParseException e2) {
                    // no way to properly parse the date.

                    if(throwParseException) {
                        throw e2;
                    }

                    return unparseableDate(source);
                }
            }

        }
    }



    @SuppressLint("SimpleDateFormat")
    private static SimpleDateFormatExt utcTimeFormat = new SimpleDateFormatExt("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static SimpleDateFormatExt getServerTimeFormat() {
        return utcTimeFormat;
    }

    public static String tryConvertReadableDateOrTime(String valueString) {
        Date date;
        try {
            date =  getServerTimeFormat().parse(valueString, true);
        } catch (ParseException e) {
            return null;
        }
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(date);
        DateFormat dateFormat;
        if(cal.get(Calendar.MINUTE) + cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MILLISECOND) + cal.get(Calendar.MINUTE) == 0) {
            // It's a date
            dateFormat = android.text.format.DateFormat.getDateFormat(StudyCompanion.getAppContext());
        } else {
            // It's a time
            dateFormat = android.text.format.DateFormat.getTimeFormat(StudyCompanion.getAppContext());
        }

        return dateFormat.format(date);


    }

    public static void setTextOrHideTextView(TextView tv, String text) {
        if(nullOrEmpty(text)) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }

    public static boolean nullOrEmpty(Object object) {
        if (object == null) {
            return true;
        }
        return object.toString().isEmpty();
    }

    public static Long getUniqueLocalId(Realm realm, Class className) {
        Number number = realm.where(className).max("localId");
        if (number == null) return 1l;
        else return (long) number + 1;
    }

    public static String filterSpecialCharacters(String path) {
        String illegalCharsFilter = "[^A-Za-z0-9]";
        String replaceWith = "-";
        return path.replaceAll(illegalCharsFilter, replaceWith);
    }

    private static Date setTimeToMidnight(Date date, boolean nextDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if(nextDay) {
            cal.add(Calendar.DATE, 1);
        }
        return cal.getTime();
    }

    public static Date setTimeToZero(Date date) {
        return setTimeToMidnight(date, false);
    }

    public static Date setTimeToNextMidnight(Date date) {
        return setTimeToMidnight(date, true);
    }

    public static Date todayTimeFromMilitaryTime(String militaryTime) {
        // time pattern must be 4-digit-time in 24h format, e.g. "1200"
        try {
            Date inputDate = new SimpleDateFormat("HHmm", Locale.getDefault()).parse(militaryTime);
            Calendar todayCal = Calendar.getInstance();
            Calendar inputCal = Calendar.getInstance();

            inputCal.setTime(inputDate);
            todayCal.set(Calendar.HOUR_OF_DAY, inputCal.get(Calendar.HOUR_OF_DAY));
            todayCal.set(Calendar.MINUTE, inputCal.get(Calendar.MINUTE));
            todayCal.set(Calendar.SECOND, inputCal.getActualMinimum(Calendar.SECOND));
            todayCal.set(Calendar.MILLISECOND, inputCal.getActualMinimum(Calendar.MILLISECOND));

            return todayCal.getTime();

        } catch (ParseException e) {
            return null;
        }
    }

    public static Long getMillisecondsTillNextMilitaryTime(String militaryTime) {
        Date nextTimeDate = todayTimeFromMilitaryTime(militaryTime);
        if(nextTimeDate == null)  {
            return null;
        }
        long nextTime = nextTimeDate.getTime();
        long currentTime = new Date().getTime();
        long diffTime = nextTime - currentTime;

        if(diffTime < 0) {
            diffTime += TimeUnit.DAYS.toMillis(1);
        }

        return diffTime;
    }

    public static int getMinutesFromMilitaryTimeDuration(String militaryTimeDuration) {
        // time pattern must be 4-digit-time duration in 24h format, e.g. "1220" is twelve hours and 20 seconds
        // maximum allowed duration is 2359
        try {
            Date inputDate = new SimpleDateFormat("HHmm", Locale.US).parse(militaryTimeDuration);

            Calendar cal = Calendar.getInstance();
            cal.setTime(inputDate);
            long h = cal.get(Calendar.HOUR_OF_DAY);
            long m = cal.get(Calendar.MINUTE);

            return (int)(h * 60 + m);

        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * returns difference of days of two date instances independent of the time set.
     *
     * Means: If provided date instances refer to same day, result will always be 0.
     * If "from" refers to any time one day before "to", result will be 1.
     * If "from" refers to any time two days after "to", result will be -2.
     * @param from
     * @param to
     * @return
     */
    public static int getDifferenceDays(Date from, Date to) {
        Date fromDay = setTimeToZero(from);
        Date toDay = setTimeToZero(to);

        long differenceMs = toDay.getTime() - fromDay.getTime();

        return (int)(differenceMs / (1000 * 60 * 60 * 24));
    }

    public static boolean isUnmeteredConnection() {
        ConnectivityManager cm = (ConnectivityManager) StudyCompanion.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && !cm.isActiveNetworkMetered();
    }

    public static boolean isAnyConnection() {
        ConnectivityManager cm = (ConnectivityManager) StudyCompanion.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }


    public static CharSequence getText(Context context, int id, Object... args) {
        // inspired by maral and fernandospr @stackoverflow https://stackoverflow.com/questions/23503642/how-to-use-formatted-strings-together-with-placeholders-in-android
        try {
            for (int i = 0; i < args.length; ++i)
                args[i] = args[i] instanceof String ? TextUtils.htmlEncode((String) args[i]) : args[i];
            SpannedString spStr = new SpannedString(context.getText(id));
            String html = Html.toHtml(spStr);
            String str = String.format(html, args);
            CharSequence res = Html.fromHtml(str);
            while (res.charAt(res.length() - 1) == '\n') {
                res = res.subSequence(0, res.length() - 1);
            }
            return res;
        } catch(Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static class ObservableValue<T> {
        private final List<Observer<T>> observers =
                new LinkedList<Observer<T>>();
        private final Object listLock = new Object();

        private Map<Observer<T>, Executor> executors = new HashMap<>();

        private T value;

        public Observer<T> addObserver(Observer<T> obs) {
            return addObserver(obs, false, null);
        }
        public Observer<T> addObserver(Observer<T> obs, boolean update) {
            return addObserver(obs, update, null);
        }
        public Observer<T> addObserver(Observer<T> obs, Executor executor) {
            return addObserver(obs, false, executor);
        }

        public Observer<T> addObserver(Observer<T> obs, boolean update, Executor executor) {
            if (obs == null) {
                throw new IllegalArgumentException("Tried to add a null observer.");
            }
            synchronized (listLock) {
                if (!observers.contains(obs)) {
                    observers.add(obs);
                }

                if(executor != null) {
                    executors.put(obs, executor);
                }
            }

            if(update) {
                obs.onUpdate(this, value);
            }

            return obs;
        }

        public boolean removeObserver(Observer<T> obs) {
            if (obs == null) {
                throw new IllegalArgumentException("Tried to remove a null observer.");
            }
            boolean res;

            synchronized (listLock) {
                executors.remove(obs);
                res = observers.remove(obs);
            }

            return res;
        }

        public void setValue(T value) {
            this.value = value;
            invalidateValue();
        }

        public void invalidateValue() {
            List<Observer<T>> currentObservers;
            Map<Observer<T>, Executor> currentExecutors;

            synchronized (listLock) {
                currentObservers = new LinkedList<>(observers);
                currentExecutors = new HashMap<>(executors);
            }

            for (Observer<T> obs : currentObservers) {
                Executor e = currentExecutors.get(obs);
                if (e == null) {
                    obs.onUpdate(this, value);
                } else {
                    e.execute(() -> obs.onUpdate(this, value));
                }
            }

        }

        public T getValue() {
            return value;
        }

        public ObservableValue(T initValue) {
            value = initValue;
        }

        public ObservableValue() {
            value = null;
        }
    }

    public interface Observer<T> {
        public void onUpdate(ObservableValue<T> object, T data);
    }

    public static float convertFromISO11073_20601_32Bit(byte[] bytes) {
        int base = 10;
        int signedExponent = bytes[3];
        int signedMantissa = (bytes[2]) << 16 | (bytes[1] & 0xFF) <<8 | (bytes[0] & 0xFF);
        double factor = Math.pow(base, signedExponent);

        if(signedExponent == 0) {
            switch(signedMantissa) {
                case 8388606: // 2^23-2
                    // return Float.POSITIVE_INFINITY; // NOT SUPPORTED BY JSON
                case -8388606: // -(2^23-2)
                    // return Float.NEGATIVE_INFINITY; // NOT SUPPORTED BY JSON
                case  8388607: // 2^23-1     NaN (Not a Number)
                case -8388608: // -(2^23)    NRes (Not at this Resolution)
                case -8388607: // -(2^23-1)  Reserved
                    // return Float.NaN;              // NOT SUPPORTED BY JSON
                    return -1;     // JSON RESTRICTION workaround: special number values will be transferred as -1
            }
        }
        return (float)(factor * signedMantissa);
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

}
