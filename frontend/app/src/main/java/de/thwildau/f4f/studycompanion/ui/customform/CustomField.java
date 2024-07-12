package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class CustomField {
    public enum FieldType {
        IntType,
        FloatType,
        StringType,
        BoolType,
        TimeType,
        DateType,
        EnumType,
        ADT,
        FoodList,
        ListType,
        Container
    }

    private static int nextRequestCode = 500;

    private final String id;
    private FieldType type;
    private FieldType elementsType = null;
    private String adtOrEnumID = null;
    private Float minValue = null;
    private Float maxValue = null;
    private boolean maybeNull = false;


    public CustomField(String id, FieldType fieldType) {
        this.id = id;
        this.type = fieldType;

        View mainView = getView();
        if(mainView != null)
            disableSaveState(getView());
    }

    /**
     * We disable the auto-saving and restoring of ViewStates for all custom form views,
     * since otherwise Android assigns the same stored value to multiple CustomField instances or
     * their Child Views if they have assigned the same ID in the template.
     * for details about auto-restoring ViewState see: https://developer.android.com/guide/fragments/saving-state#view
     * @param viewGroup
     */
    private void disableSaveState(ViewGroup viewGroup) {
        int n = viewGroup.getChildCount();
        for(int i = 0; i < n; i++) {
            disableSaveState(viewGroup.getChildAt(i));
        }
    }

    private void disableSaveState(View view) {
        if(view instanceof ViewGroup) {
            disableSaveState((ViewGroup)view);
        } else {
            view.setSaveEnabled(false);
        }
    }


    public static int getNextRequestCode() {
        nextRequestCode++;
        if(nextRequestCode >= 800) {
            nextRequestCode = 500;
        }
        return nextRequestCode;
    }

    public  FieldType getElementsType() { return null; };

    public abstract Object getValue();
    public abstract void setValue(Object value);
    public abstract View getView();
    public abstract void setEnabled(boolean enabled);
    public abstract boolean isEnabled();

    public void setError(String errorMessage) { };

    public  String getADTorEnumIdentifier() { return null; } // needs only to be implemented, if CustomField is ADT, Enum or ListType with ElementsType of ADT or Enum

    public boolean verifyValue() {
        Object value = getValue();

        if(!isEnabled()) {
            // Read-only fields always verify true
            return true;
        }

        String val;
        if(value != null) {
            val = value.toString();
            boolean empty = val.isEmpty();
            Log.d("","");
        }

        // check for null and if it is allowed
        if (
                (null == value)  || (value instanceof Boolean && (Boolean)value == false) ||
                (value.toString().isEmpty())
        ) {
            return maybeNull;
        }



        if(type == FieldType.FloatType || type == FieldType.IntType) {
            float valFl;
            try {
                valFl = Float.parseFloat(value.toString());
            } catch(NumberFormatException e) {
                return false;
            }

            if(
                    ((maxValue != null) && (valFl > maxValue)) ||
                    ((minValue != null) && (valFl < minValue))
            ) {
                return false;
            }
        }

        return true;
    }

    /* Getters */

    public FieldType getType() {
        return type;
    }

    public String getID() {
        return id;
    }

    public String getId() {
        return id;
    }

    public String getAdtOrEnumID() {
        return adtOrEnumID;
    }

    public Float getMinValue() {
        return minValue;
    }

    public Float getMaxValue() {
        return maxValue;
    }

    public boolean isMaybeNull() {
        return maybeNull;
    }


    /* Setters */


    public void setElementsType(FieldType elementsType) {
        this.elementsType = elementsType;
    }

    public void setAdtOrEnumID(String adtOrEnumID) {
        this.adtOrEnumID = adtOrEnumID;
    }

    public void setMinValue(Float minValue) {
        this.minValue = minValue;
    }

    public void setMaxValue(Float maxValue) {
        this.maxValue = maxValue;
    }

    public void setMaybeNull(boolean maybeNull) {
        this.maybeNull = maybeNull;
    }


    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // To be overridden, if needed
    }

    @NonNull
    @Override
    public String toString() {
        return getValue().toString();
    }
}
