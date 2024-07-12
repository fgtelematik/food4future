package de.thwildau.f4f.studycompanion.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import org.json.JSONObject;

import java.util.Date;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;

public class StudyHomeStateUI {

    private Context context;
    private final View rootView;

    StudyHomeStateUI(Context context, View homeFragmentView) {
        rootView = homeFragmentView;
        this.context = context;
    }

    public void onResume() {
        CircularProgressBar progressBar = rootView.findViewById(R.id.progressStudyProgress);
        TextView textProgress = rootView.findViewById(R.id.textStudyProgress);
        TextView textExtra = rootView.findViewById(R.id.textStudyStateExtra);
        View studyStateView = rootView.findViewById(R.id.studyStateView);

        progressBar.setProgressMax(1f);

        // Initially hide everything
        progressBar.setVisibility(View.GONE);
        textProgress.setVisibility(View.GONE);
        textExtra.setVisibility(View.GONE);
        studyStateView.setVisibility(View.GONE);

        if(BackendIO.getCurrentUser() == null || BackendIO.getCurrentUser().role != Role.Participant)
            return;

        try {
            String anamnesisDataStr = BackendIO.getCurrentUser().anamnesisData;
            JSONObject anamnesisData = new JSONObject(anamnesisDataStr);
            Date beginDate = Utils.setTimeToZero(
                    Utils.getServerTimeFormat().parse(
                            anamnesisData.getString("study_begin_date")
                    )
            );
            Date endDate = Utils.setTimeToNextMidnight(
                    Utils.getServerTimeFormat().parse(
                            anamnesisData.getString("study_end_date")
                    )
            );

            Date now = new Date();

            int totalDays = Utils.getDifferenceDays(beginDate, endDate);
            int currentDay =  1 + Utils.getDifferenceDays(beginDate, now);

            float progress;

            studyStateView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            textProgress.setVisibility(View.VISIBLE);

            if(currentDay < 1) {
                progress = 0f;
                textExtra.setVisibility(View.VISIBLE);
                textExtra.setText(context.getString(R.string.study_state_not_started));
                textProgress.setText(context.getString(R.string.study_state_day_progress, 0, totalDays));
            } else if (currentDay > totalDays){
                progress = 1f;
                textExtra.setVisibility(View.VISIBLE);
                textExtra.setText(context.getString(R.string.study_state_finished_text));
                textProgress.setText(context.getString(R.string.study_state_finished_progress));
                textProgress.setTextColor(context.getColor(R.color.colorGreen));
                textExtra.setTextColor(context.getColor(R.color.colorGreen));
                progressBar.setBackgroundProgressBarColor(context.getColor(R.color.colorGreenBright));
                progressBar.setProgressBarColor(context.getColor(R.color.colorGreen));
            } else {
                progress = (float)currentDay / totalDays;
                textProgress.setText(context.getString(R.string.study_state_day_progress, currentDay, totalDays));
            }

            progressBar.setProgress(progress);


        } catch (Throwable t) {
            // no valid study period available. Do not show any study state related elements.
            studyStateView.setVisibility(View.GONE);
        }

    }

    public void onPause() {

    }
}
