package de.thwildau.f4f.studycompanion.ui.customform;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;

class CustomFormFieldContainer {
    private ViewGroup containerView = null;
    private final ViewGroup parentView;
    private String title = null;
    private String helpText = null;
    private final LayoutInflater layoutInflater;

    CustomFormFieldContainer(LayoutInflater layoutInflater) {
        this.layoutInflater = layoutInflater;
        this.parentView = null;
    }

    CustomFormFieldContainer(LayoutInflater layoutInflater, ViewGroup parentView) {
        this.layoutInflater = layoutInflater;
        this.parentView = parentView;
    }

    public void addFieldView(View childView) {
        if(containerView == null) {
            getContainerView();
        }

        ViewGroup fieldContainer = containerView.findViewById(R.id.contentContainer);
        fieldContainer.addView(childView);
    }

    private void checkHeaderVisibility() {
        if (containerView == null) {
            return;
        }

        View headerContainer = containerView.findViewById(R.id.headerContainer);
        boolean headerVisible = !Utils.nullOrEmpty(title) || !Utils.nullOrEmpty(helpText);
        headerContainer.setVisibility(headerVisible ? View.VISIBLE : View.GONE);
    }

    public ViewGroup getContainerView() {
        if (containerView != null) {
            return containerView;
        }

        containerView = (ViewGroup) layoutInflater.inflate(R.layout.custom_field_container, parentView, false);
        setTitle(title);
        setHelpText(helpText);

        return containerView;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        if (containerView == null) {
            return;
        }

        TextView titleTextView = containerView.findViewById(R.id.titleText);
        if (Utils.nullOrEmpty(title)) {
            titleTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setVisibility(View.VISIBLE);
            titleTextView.setText(title);
        }

        checkHeaderVisibility();
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
        if (containerView == null) {
            return;
        }

        TextView helpTextView = containerView.findViewById(R.id.helpText);
        if (Utils.nullOrEmpty(helpText)) {
            helpTextView.setVisibility(View.GONE);
        } else {
            helpTextView.setVisibility(View.VISIBLE);
            helpTextView.setText(helpText);
        }

        checkHeaderVisibility();
    }
}
