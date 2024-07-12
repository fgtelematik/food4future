package de.thwildau.f4f.studycompanion.datamodel;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;

public class EnumerationElement {

    private String enumerationId;
    private String elementId;
    private String label;
    private String explicitLabel;

    EnumerationElement(String enumerationId, String elementId, String label, @Nullable String explicitLabel) {
        this.enumerationId = enumerationId;
        this.elementId = elementId;
        this.label = label;
        this.explicitLabel = explicitLabel;
    }

    EnumerationElement(String enumerationId, String elementId, String label) {
        this(enumerationId, elementId, label, null);
    }

    /**
     * Null element, can be instantiated from UI.
     */
    public EnumerationElement(String enumerationId) {
        this.enumerationId = enumerationId;
        elementId = null;
        label = StudyCompanion.getAppContext().getString(R.string.null_element_label);
    }

    public String getEnumerationId() {
        return enumerationId;
    }

    public String getElementId() {
        return elementId;
    }

    public String getLabel() {
        return label;
    }

    public String getSummaryLabel() {
        return explicitLabel != null ? explicitLabel : label;
    }

    public Drawable getDrawable(Context context) {
        return EnumImageProvider.getDrawableForEnumEntry(context, enumerationId, elementId);
    }

    @NonNull
    @Override
    public String toString() {
        return label;
    }
}
