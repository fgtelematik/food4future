package de.thwildau.f4f.studycompanion.ui.questions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.ui.customform.CustomFormActivity;
import de.thwildau.f4f.studycompanion.ui.customform.CustomFormFragment;


public class QuestionsFragment extends Fragment {

    private QuestionsViewModel questionsViewModel;

    private TableLayout daylog;
    private ScrollView daylogScrollView;
    private Button buttonTodayQuestions;
    private View todaysQuestionsView;
    private View loadingView;
    private View todayView;
    private View lastDayRow;
    private ProgressBar loadingProgressBar;

    private boolean isLoadingCancelled;
    private boolean isLoading;

    private int currentDay = 0;

    private TextView textDaylogNotAvailable;

    private static  final String LOG_TAG = "QuestionsFragment";

    private static int REQUEST_EDIT_DAY_DATA = 1;

    public static final String EXTRA_OPEN_TODAY_QUESTIONS = "EXTRA_OPEN_TODAY_QUESTIONS";

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        questionsViewModel = new ViewModelProvider(this).get(QuestionsViewModel.class);

        View root = inflater.inflate(R.layout.fragment_questions, container, false);


        daylog = root.findViewById(R.id.daylog_table);
        daylogScrollView = root.findViewById(R.id.daylog_scrollview);
        textDaylogNotAvailable = root.findViewById(R.id.text_daylog_not_available);
        loadingProgressBar = root.findViewById(R.id.question_log_progress_bar);

        loadingView = root.findViewById(R.id.daylog_loading_view);
        todaysQuestionsView = root.findViewById(R.id.viewTodaysQuestions);
        buttonTodayQuestions = root.findViewById(R.id.buttonTodayQuestions);
        buttonTodayQuestions.setVisibility(View.GONE);

        questionsViewModel.refreshAllData();
        questionsViewModel.getStudyTime().observe(getViewLifecycleOwner(), this::populateTable); // gets implicitly called

        return root;
    }


    private Date plusDays(Date date, int plusDays) {

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, plusDays);
        return cal.getTime();
    }

    private boolean isToday(Date date) {

        Date today = Utils.setTimeToZero(new Date());
        date = Utils.setTimeToZero(date);

        return date.equals(today);
    }

    private void populateTableAsync(QuestionsViewModel.StudyTime studyTime) {
        final QuestionsViewModel.StudyTime studyTimeFinal = studyTime;
        new Thread(() -> populateTable(studyTime)).start();
    }


    private void afterTableCreated() {

        setLoadingState(false);

        new Handler(Looper.getMainLooper()).post(() -> {

            // fix scroll view cuts off the last item by adding a bottom padding
            if(lastDayRow != null) {
                daylog.setPadding(
                        daylog.getPaddingLeft(),
                        daylog.getPaddingTop(),
                        daylog.getPaddingRight(),
                        lastDayRow.getHeight() / 2
                );
            }

            // scroll that today date is in the center of the view
            if(todayView != null) {
                int scrollPos = todayView.getTop();
                scrollPos -= daylogScrollView.getHeight() / 2;
                scrollPos += todayView.getHeight() / 2;

                if (scrollPos < 0) {
                    scrollPos = 0;
                }
                daylogScrollView.scrollTo(0, scrollPos);
            }

            Bundle extras = getArguments();
            if(extras != null && extras.containsKey(EXTRA_OPEN_TODAY_QUESTIONS) && extras.getBoolean(EXTRA_OPEN_TODAY_QUESTIONS)) {
                extras.putBoolean(EXTRA_OPEN_TODAY_QUESTIONS, false); // Avoid re-open daily question dialogue after it was already shown
                buttonTodayQuestions.callOnClick();
            }

        });
    }

    private void buildRowRecursive(Date startDay, int numDays, int dayIndex) {

        loadingProgressBar.setProgress((int) (100 * ((float) dayIndex/numDays)));
        Date day = plusDays(startDay, dayIndex);
        QuestionLogTableRowWrapper dayRow = new QuestionLogTableRowWrapper(getContext(), day);

        dayRow.initLayout(daylog, (view, resid, parent) -> {
            Utils.UserInputState fillingState = questionsViewModel.getUserInputStateForDay(day);
            dayRow.setDataFillingState(fillingState);
            dayRow.setOnActionButtonClickListener(btnView -> {
                editDataForDay(day);
            });

            if (isToday(day)) {
                todaysQuestionsView.setVisibility(fillingState == Utils.UserInputState.COMPLETE_DATA ? View.GONE : View.VISIBLE);
                todayView = dayRow.getView();
                buttonTodayQuestions.setVisibility(View.VISIBLE);
                buttonTodayQuestions.setOnClickListener(btnView -> {
                    editDataForDay(day);
                });
            }

            daylog.addView(dayRow.getView());
            lastDayRow = dayRow.getView();

            if(dayIndex <= numDays) {
                if(!isLoadingCancelled)
                    buildRowRecursive(startDay, numDays, dayIndex + 1);
            } else {
                afterTableCreated();
            }

        });
    }

    private void setLoadingState(boolean loading) {
        isLoading = loading;
        daylogScrollView.setVisibility(loading ? View.GONE : View.VISIBLE);
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        if(loading)
            todaysQuestionsView.setVisibility(View.GONE);
    }


    private void populateTable(QuestionsViewModel.StudyTime studyTime) {

        boolean daylogNotAvailable = studyTime == null;

        final Date start;
        final Date end;

        setLoadingState(true);

        isLoadingCancelled = false;

        if(!daylogNotAvailable) {
            start =  studyTime.getStartTime();
            end = studyTime.getEndTime();

            daylogNotAvailable = start.compareTo(end) > 0; // set daylogNotAvailable if start date is after end date
        } else {
            start = null;
            end = null;
        }

        final boolean daylogNotAvailableFinal = daylogNotAvailable;

        new Handler(Looper.getMainLooper()).post( () -> {
            daylog.removeAllViews();
            textDaylogNotAvailable.setVisibility(daylogNotAvailableFinal ? View.VISIBLE : View.GONE);
            if(daylogNotAvailableFinal) {
                loadingView.setVisibility(View.GONE);
            }
        });

        if(daylogNotAvailable) {
            return;
        }

        final long numDays = (end.getTime() - start.getTime()) /  (1000*60*60*24);


        // Build all the table rows
        buildRowRecursive(start, (int)numDays, 0);

    }



    private void editDataForDay(Date date) {
        Intent i = new Intent(getActivity(), CustomFormActivity.class);
        JSONObject dayData = questionsViewModel.getDatasetForDay(date);
        if(dayData == null) {
            dayData = new JSONObject();
            try {
                dayData.put("effective_day", Utils.getServerTimeFormat().format(date));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        i.putExtra(CustomFormFragment.EXTRA_DATA_JSON, dayData.toString());
        i.putExtra(CustomFormFragment.EXTRA_BUTTON_CONFIRM, getString(R.string.button_save));
        i.putExtra(CustomFormFragment.EXTRA_ADT_ID, "user_data");

        String dayString = DateFormat.getDateInstance(DateFormat.SHORT).format(date);

        i.putExtra(CustomFormFragment.EXTRA_TITLE,  getString(R.string.edit_user_data_title, dayString));
        startActivityForResult(i, REQUEST_EDIT_DAY_DATA);
    }

    @Override
    public void onPause() {
        super.onPause();
        isLoadingCancelled = isLoading;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(isLoadingCancelled) {
            isLoadingCancelled = false;
            populateTable(questionsViewModel.getStudyTime().getValue());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_EDIT_DAY_DATA && resultCode == Activity.RESULT_OK) {
            questionsViewModel.updateOrInsertUserData(data.getStringExtra(CustomFormFragment.EXTRA_UPDATED_DATA_JSON));
            Toast.makeText(getActivity(), R.string.thank_you_user_data, Toast.LENGTH_LONG).show();
        }
    }
}