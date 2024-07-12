package de.thwildau.f4f.studycompanion.ui.questions;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;

public class QuestionsViewModel extends ViewModel {
    private static String LOG_TAG = "QuestionsViewModel";

    public class StudyTime {
        private Date startTime;
        private Date endTime;

        public StudyTime(Date startTime, Date endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public Date getStartTime() {
            return startTime;
        }

        public Date getEndTime() {
            return endTime;
        }
    }

    private MutableLiveData<StudyTime> mStudyTime;

    private Map<Date, JSONObject> userDataCache = new HashMap<>();

    public QuestionsViewModel() {
        mStudyTime = new MutableLiveData<>();
    }

    public Utils.UserInputState getUserInputStateForDay(Date day) {
        if(!userDataCache.containsKey(day)) {
            // no dataset existing yet for this day
            return Utils.UserInputState.NO_DATA;
        }
        JSONObject dayData = userDataCache.get(day);

        return Utils.getUserInputState(dayData);
    }

    public JSONObject getDatasetForDay(Date day) {
        return userDataCache.get(day);
    }

    public void updateOrInsertUserData(String dataString) {
        try {
            JSONObject dataObj = new JSONObject(dataString);
            DataManager.updateOrInsertData(DataType.UserData, dataObj);
            refreshAllData();
        } catch (JSONException | DataManager.NoPermissionException e) {
            e.printStackTrace();
        }
    }

    public void refreshAllData() {
        try {

            // Read all collected user question data from local database
            List<JSONObject> userDataObjects = DataManager.getAllDatasets(DataType.UserData);

            userDataCache.clear();
            for(JSONObject obj : userDataObjects) {
                Date effectiveDay = Utils.setTimeToZero(Utils.getServerTimeFormat().parse(obj.getString("effective_day")));
                userDataCache.put(effectiveDay, obj);
            }

            // read study time from current user
            String anamnesisDataStr = BackendIO.getCurrentUser().anamnesisData;

            if(anamnesisDataStr == null) {
                throw new IllegalStateException("Tried to access daily questions for a user without static data!");
            }

            JSONObject anamnesisData = new JSONObject(anamnesisDataStr);

            String startDateStr = anamnesisData.optString("study_begin_date");
            String endDateStr = anamnesisData.optString("study_end_date");

            if(Utils.nullOrEmpty(startDateStr) || Utils.nullOrEmpty(endDateStr)) {
                mStudyTime.setValue(null);
                return;
            }

            Date startDate = Utils.setTimeToZero(Utils.getServerTimeFormat().parse(startDateStr));
            Date endDate = Utils.setTimeToZero(Utils.getServerTimeFormat().parse(endDateStr));

            mStudyTime.setValue(new StudyTime(startDate, endDate));

        } catch(IllegalStateException | JSONException | ParseException | DataManager.NoPermissionException e) {
            mStudyTime.setValue(null);
        }
    }


    public MutableLiveData<StudyTime> getStudyTime() {
        return mStudyTime;
    }


}