package de.thwildau.f4f.studycompanion.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import de.thwildau.f4f.studycompanion.R;

public class ProgressDialog extends Dialog {
    Context context;
    OnProgressDialogCancelListener cancelListener = null;
    ProgressBar  progressBar = null;
    TextView progressText = null;
    TextView titleText = null;
    Button cancelButton;
    CharSequence title = null;
    int progress = 0;
    int maxProgress = 100;
    private boolean cancelable = false;

    public interface OnProgressDialogCancelListener {
        boolean onCancel();
    }


    public ProgressDialog(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_progress);
        cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(this::onClickCancelButton);
        super.setCancelable(false); // We allow cancelling only by pressing the "Cancel" button.
        setCancelable(cancelable);

        progressBar = findViewById(R.id.progressBar);
        titleText = findViewById(R.id.titleText);
        progressText = findViewById(R.id.progressText);

        setTitle(title);
        setProgress(progress);
        setMaxProgress(maxProgress);
    }

    @Override
    public void setCancelable(boolean flag) {

        cancelable = flag;
        if(cancelButton != null) {
            cancelButton.setVisibility(flag ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void setTitle(@Nullable CharSequence title) {
        this.title = title;

        if(titleText != null) {
            titleText.setText(title == null ? "" : title);
        }
    }

    @Override
    public void setTitle(int titleId) {
        setTitle(context.getResources().getString(titleId));
    }

    public void setProgress(int progress) {
        this.progress = progress;

        if(progressBar != null) {
            progressBar.setProgress(progress);
            double progDec = (double)progress / maxProgress;
            int percentage = (int)(progDec * 100);
            progressText.setText(percentage + " %");
        }
    }

    public void setMaxProgress(int maxProgress) {
        this.maxProgress = maxProgress;

        if(progressBar != null) {
            progressBar.setMax(maxProgress);
            setProgress(progress);
        }
    }

    private void onClickCancelButton(View v) {
        if(cancelListener != null) {
            if(cancelListener.onCancel()) {
                dismiss();
            }
        } else {
            dismiss();
        }
    }

    public void setOnProgressDialogCancelListener(@Nullable OnProgressDialogCancelListener listener) {
        cancelListener = listener;
    }
}
