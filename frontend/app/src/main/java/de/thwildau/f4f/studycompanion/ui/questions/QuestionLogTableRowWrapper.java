package de.thwildau.f4f.studycompanion.ui.questions;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.asynclayoutinflater.view.AsyncLayoutInflater;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;

public class QuestionLogTableRowWrapper {

    private Date day;
    private TextView textDate;
    private TextView textStatus;
    private ImageButton buttonAdd;
    private ImageButton buttonEdit;
    private Utils.UserInputState fillingState;
    private View.OnClickListener mOnClickListener = null;
    private Context mContext;

    private TableRow tableRow;

    public QuestionLogTableRowWrapper(Context context, Date day) {
        mContext = context;
        this.day = day;
    }

    public TableRow getView() {
        return tableRow;
    }

    private void onButtonPress(View v) {
        if(mOnClickListener != null) {
            mOnClickListener.onClick(tableRow);
        }
    }

    private boolean isDayInFuture() {
        // set end of current day as reference
        Date now = Calendar.getInstance().getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 0);
        Date nextMidnight = cal.getTime();

        return nextMidnight.before(day);
    }

    public void initLayout(ViewGroup parent, AsyncLayoutInflater.OnInflateFinishedListener inflateFinishedListener) {
        AsyncLayoutInflater inflater = new AsyncLayoutInflater(mContext);
        final long start = System.currentTimeMillis();

        inflater.inflate(R.layout.view_questionlog_tablerow, parent, (view, resid, parent2) ->
        {
            long now = System.currentTimeMillis();
            long time = now - start;
            Log.d("QuestionLogRowInflater", "Inflation took: " + time + " ms");
            tableRow = (TableRow) view;

            textDate = view.findViewById(R.id.textDate);
            textStatus = view.findViewById(R.id.textStatus);
            buttonAdd = view.findViewById(R.id.btnAdd);
            buttonEdit = view.findViewById(R.id.btnEdit);


            textDate.setText(DateFormat.getDateInstance(DateFormat.SHORT).format(day));

            if(isDayInFuture()) {
                setFutureStatus();
            }

            buttonAdd.setOnClickListener(this::onButtonPress);
            buttonEdit.setOnClickListener(this::onButtonPress);

            if(inflateFinishedListener != null) {
                inflateFinishedListener.onInflateFinished(view, resid, parent2);
            }
        });

        // inflate(mContext, R.layout.view_questionlog_tablerow, this);


    }

    public void setDataFillingState(Utils.UserInputState fillingState) {
        this.fillingState = fillingState;
        if(isDayInFuture()) {
            setFutureStatus();
            return;
        }

        if(fillingState == Utils.UserInputState.COMPLETE_DATA) {
            textStatus.setText(R.string.day_status_answered);
            textStatus.setTypeface(null, Typeface.BOLD);
            textStatus.setTextColor(ContextCompat.getColor(mContext, R.color.colorGreen));
            buttonAdd.setVisibility(View.GONE);
            buttonEdit.setVisibility(View.VISIBLE);
        } else {
            if(fillingState == Utils.UserInputState.INCOMPLETE_DATA) {
                textStatus.setText(R.string.day_status_incomplete);
                buttonEdit.setVisibility(View.VISIBLE);
                buttonAdd.setVisibility(View.GONE);
            } else {
                textStatus.setText(R.string.day_status_open);
                buttonEdit.setVisibility(View.GONE);
                buttonAdd.setVisibility(View.VISIBLE);
            }

            textStatus.setTypeface(null, Typeface.ITALIC);
            textStatus.setTextColor(ContextCompat.getColor(mContext, R.color.colorOrange));

        }
    }

    private void setFutureStatus() {
        textStatus.setText(R.string.day_status_future);
        textStatus.setTypeface(null, Typeface.ITALIC);
        textStatus.setTextColor(ContextCompat.getColor(mContext, R.color.colorGray));
        buttonEdit.setVisibility(View.GONE);
        buttonAdd.setVisibility(View.INVISIBLE);
    }

    public void setOnActionButtonClickListener(View.OnClickListener onClickListener) {
        this.mOnClickListener = onClickListener;
    }
}
