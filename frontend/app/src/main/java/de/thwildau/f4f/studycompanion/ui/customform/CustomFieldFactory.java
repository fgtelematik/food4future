package de.thwildau.f4f.studycompanion.ui.customform;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.EnumerationElement;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.qr.QRCodeReaderActivity;

public class CustomFieldFactory {

        public class InvalidValueException extends RuntimeException {}

    private LayoutInflater inflater;
    private Fragment baseFragment;
    private List<CustomField> resultHandlerFields = new ArrayList<>();

    //TODO: transform all CustomField overloads in the create..-methods
    // into to separate subclasses to clean up this messy class

    public CustomFieldFactory(Fragment baseFragment, LayoutInflater layoutInflater) {
        this.inflater = layoutInflater;
        this.baseFragment = baseFragment;
    }


    public CustomField createSliderOld(final String id, String label, String description, float minVal, float maxVal, String minDescription, String maxDescription, float stepSize) {
        final View fieldView = inflater.inflate(R.layout.custom_field_slider, null);
        final Slider slider = fieldView.findViewById(R.id.valSlider);
        TextView tvMin = fieldView.findViewById(R.id.valSliderMinDescription);
        TextView tvMax = fieldView.findViewById(R.id.valSliderMaxDescription);
        TextView tvDesc = fieldView.findViewById(R.id.valSliderTitleHelper);

        Utils.setTextOrHideTextView(tvDesc, description);
        Utils.setTextOrHideTextView(fieldView.findViewById(R.id.valSliderTitleText), label);

        tvMin.setText(minDescription);
        tvMax.setText(maxDescription);

        slider.setValueFrom(minVal);
        slider.setValueTo(maxVal);

        slider.setStepSize(stepSize);

        return new CustomField(id, CustomField.FieldType.IntType) {

            @Override
            public Object getValue() {
                return slider.getValue();
            }

            @Override
            public void setValue(Object value) {
                float val;
                if(value == null) {
                    val = minVal; // set minimal slider value on null (not an optimal solution, but slider is currently not used anyway)
                } else {
                    val = Float.parseFloat(value.toString());
                }
                slider.setValue(val);
            }

            @Override
            public View getView() {
                return fieldView;
            }

            @Override
            public void setEnabled(boolean enabled) {
                slider.setEnabled(enabled);
            }

            @Override
            public boolean isEnabled() {
                return slider.isEnabled();
            }
        };
    }

    public CustomField createCheckbox(final String id, String label, String description) {
        final View fieldView = inflater.inflate(R.layout.custom_field_switch, null);

        final CheckBox checkBox = fieldView.findViewById(R.id.valCheckbox);
        final TextView helpText = fieldView.findViewById(R.id.valTextHelp);
        final TextView errorText = fieldView.findViewById(R.id.valTextError);
        final TextView labelText = fieldView.findViewById(R.id.valTextLabel);

        Utils.setTextOrHideTextView(helpText, description);
        Utils.setTextOrHideTextView(labelText, label);

        errorText.setVisibility(View.GONE);

        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            errorText.setVisibility(View.GONE); // unset error on value change
        });

        return new CustomField(id, CustomField.FieldType.BoolType) {
            @Override
            public Object getValue() {
                return checkBox.isChecked();
            }

            @Override
            public void setValue(Object value) {
                if(value == null || value.equals(false)) {
                    checkBox.setChecked(false);
                } else {
                    checkBox.setChecked(true);
                }
            }

            @Override
            public View getView() {
                return fieldView;
            }

            @Override
            public void setEnabled(boolean enabled) {
                checkBox.setEnabled(enabled);
            }

            @Override
            public boolean isEnabled() {
                return checkBox.isEnabled();
            }

            @Override
            public void setError(String errorMessage) {
                super.setError(errorMessage);

                if(Utils.nullOrEmpty(errorMessage)) {
                    errorText.setVisibility(View.GONE);
                    Utils.setTextOrHideTextView(helpText, description);
                } else {
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText(errorMessage);
                    helpText.setVisibility(View.GONE);
                }
            }
        };
    }

    public CustomField createSlider(final String id, String label, String description, final float minVal, final float maxVal, String minDescription, String maxDescription, final float stepSize) {

        final View fieldView = inflater.inflate(R.layout.custom_field_rating_scale, null);
        final RadioGroup radioGroup = fieldView.findViewById(R.id.valRadioGroup);
        final int numElements = (int)((maxVal - minVal) / stepSize) + 1;
        final RadioButton[] radioButtons = new RadioButton[numElements];
        final TextView errorText = fieldView.findViewById(R.id.valRadioErrorText);


        errorText.setVisibility(View.GONE);

        radioGroup.removeAllViews();

        // RadioGroup won't do its work since the RadioButtons are no direct children,
        // so we re-build its functionality with an own OnClickListener:
        final View.OnClickListener radioButtonClickListener = v -> {
            for(RadioButton radioButton : radioButtons) {
                radioButton.setChecked(false);
                ((RadioButton)v).setChecked(true);
            }
            errorText.setVisibility(View.GONE); // unset error on value change
        };

        for(int i = 0; i < numElements; i++) {

            ViewGroup radioView = (ViewGroup)inflater.inflate(R.layout.custom_field_rating_scale_radio_button, radioGroup, false);
            TextView textDescription = radioView.findViewById(R.id.valTextForRadio);

            if(i == 0 && !Utils.nullOrEmpty(minDescription)) {
                textDescription.setText(minDescription);
            }

            if(i == numElements - 1 && !Utils.nullOrEmpty(maxDescription)) {
                textDescription.setText(maxDescription);
            }

            RadioButton radioButton = radioView.findViewById(R.id.valRadio);
            radioButtons[i] = radioButton;

            radioButton.setOnClickListener(radioButtonClickListener);
            radioGroup.addView(radioView);
        }

        Utils.setTextOrHideTextView(fieldView.findViewById(R.id.valRadioTitleHelper), description);
        Utils.setTextOrHideTextView(fieldView.findViewById(R.id.valRadioTitleText), label);


        return new CustomField(id, CustomField.FieldType.IntType) {

            @Override
            public void setError(String errorMessage) {
                errorText.setVisibility(Utils.nullOrEmpty(errorMessage) ? View.GONE : View.VISIBLE);
                if(errorMessage != null) {
                    errorText.setText(errorMessage);
                }
            }

            @Override
            public Object getValue() {
                int selectionIdx = 0;

                for(RadioButton radioButton : radioButtons) {
                    if(radioButton.isChecked()) {
                        break;
                    }
                    selectionIdx++;
                }

                if(selectionIdx == numElements) {
                    // no element selected
                    return null;
                } else {
                    return minVal + selectionIdx * stepSize;
                }
            }

            @Override
            public void setValue(Object value) {
                int selectedElementIdx = -1;
                if(value != null) {
                    try {
                        Number valNumber = (Number)value;
                            // interestingly not using intermediate cast to Number
                            // would cause a ClassCastException on Integer-typed values

                        Float valFloat = valNumber.floatValue();

                        selectedElementIdx = Math.round((valFloat - minVal) / stepSize);
                    } catch(Throwable e) {
                        // interpret invalid value as null
                    }
                }

                for(int i = 0; i < numElements; i++) {
                    radioButtons[i].setChecked(i == selectedElementIdx);
                }
            }

            @Override
            public View getView() {
                return fieldView;
            }

            @Override
            public void setEnabled(boolean enabled) {
                for(RadioButton radioButton : radioButtons) {
                    radioButton.setEnabled(enabled);
                }
            }

            @Override
            public boolean isEnabled() {
                return numElements <= 0 || radioButtons[0].isEnabled();
            }
        };
    }

    public CustomField createTextField(final String id, String label, @Nullable String unitString, @Nullable String helperText, int inputType, final CustomField.FieldType fieldType, final boolean qrCodeInput) {

        final View fieldView;
        final TextView helpTextView;
        final TextInputLayout textLayout;
        final TextInputEditText textInput;
        final ImageButton buttonQr;
        int requestCode = 0;

        if(unitString == null) {
            fieldView = inflater.inflate(R.layout.custom_field_text, null);
            buttonQr = fieldView.findViewById(R.id.btnQR);
            buttonQr.setVisibility(qrCodeInput ? View.VISIBLE : View.GONE);
            if(qrCodeInput) {
                requestCode = CustomField.getNextRequestCode();
                int finalRequestCode = requestCode;
                buttonQr.setOnClickListener(v -> {
                    Intent i = new Intent(baseFragment.getContext(), QRCodeReaderActivity.class);
                    i.putExtra(QRCodeReaderActivity.EXTRA_TITLE, label);
                    if(helperText != null) {
                        i.putExtra(QRCodeReaderActivity.EXTRA_INFOTEXT, helperText);
                    }
                    baseFragment.startActivityForResult(i, finalRequestCode);
                });
            }
        } else {
            fieldView = inflater.inflate(R.layout.custom_field_text_with_unit, null);
            TextView unitView = fieldView.findViewById(R.id.valTextUnit);
            unitView.setText(unitString);
        }

        textLayout = fieldView.findViewById(unitString == null ? R.id.valText : R.id.valTextWithUnit);
        textInput = fieldView.findViewById(R.id.valTextValue);
        helpTextView = fieldView.findViewById(R.id.valTextHelp);

        textInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                textLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

//        textLayout.setHint(label);
        textLayout.setHintEnabled(false);
        TextView labelText = fieldView.findViewById(R.id.valTextLabel);
        if(Utils.nullOrEmpty(label)) {
            labelText.setVisibility(View.GONE);
        } else {
            labelText.setVisibility(View.VISIBLE);
            labelText.setText(label);
        }


        if(helperText != null) {
            helpTextView.setVisibility(View.VISIBLE);
//            textLayout.setHelperTextEnabled(true);
//            textLayout.setHelperText(helperText);
            helpTextView.setText(helperText);
        } else {
            helpTextView.setVisibility(View.GONE);
        }

        textInput.setInputType(inputType);

        final boolean isFloatInputType = (inputType == (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        if(isFloatInputType) {
            char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
            String digits = "0123456789" + separator;
            if (separator != '.') {
                digits += '.';
            }

            textInput.setKeyListener(DigitsKeyListener.getInstance(digits));
        }

        final int finalRequestCode = requestCode;

        CustomField res = new CustomField(id, fieldType) {
            @Override
            public Object getValue() {
                String res = textInput.getText().toString();

                if(isFloatInputType) {
                    // replace "," by "." for German decimal inputs
                    char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
                    res = res.replace(separator, '.');
                }

                return res;
            }

            @Override
            public void setValue(Object value) {
                if(value == null) {
                    value = "";
                }
                textInput.setText(value.toString());
            }

            @Override
            public View getView() {
                return fieldView;
            }

            @Override
            public void setError(String errorMessage) {
                textLayout.setError(errorMessage);
            }

            @Override
            public void setEnabled(boolean enabled) {
                textInput.setEnabled(enabled);
            }

            @Override
            public boolean isEnabled() {
                return textInput.isEnabled();
            }

            @Override
            public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
                if(qrCodeInput && Activity.RESULT_OK == resultCode && finalRequestCode == requestCode && data != null) {
                    String content = data.getStringExtra(QRCodeReaderActivity.EXTRA_BARCODE_DATA);

                    if(content == null) {
                        return;
                    }

                    try {
                        if(fieldType.equals(FieldType.IntType)) {
                            Integer.parseInt(content);
                        } else if(fieldType.equals(FieldType.FloatType)) {
                            Float.parseFloat(content);
                        }
                    } catch(Exception e) {
                        Toast.makeText(baseFragment.getActivity(), baseFragment.getString(R.string.error_qr_data_format), Toast.LENGTH_LONG).show();
                        return;
                    }

                    setValue(content);
                }
            }
        };

        if(qrCodeInput) {
            resultHandlerFields.add(res);
        }

        return res;
    }



    public CustomField createEnumSpinner(final String id, String label, @Nullable String helperText, final String enumId, boolean maybeNull) {
        final View fieldView = inflater.inflate(R.layout.custom_field_spinner, null);
        final Spinner spinner = fieldView.findViewById(R.id.valSpinner);
        final TextView textTitle = fieldView.findViewById(R.id.valSpinnerTitle);
        final TextView textHelper = fieldView.findViewById(R.id.valSpinnerHelperText);

        final List<EnumerationElement> enumElements = SchemaProvider.getEnumElements(enumId);
        final EnumerationElement nullElement = new EnumerationElement(enumId);

        Utils.setTextOrHideTextView(textTitle, label);
        Utils.setTextOrHideTextView(textHelper, helperText);

        if(maybeNull) {
            // Add null element on top of list
            enumElements.add(0, nullElement);
        }

        ArrayAdapter<EnumerationElement> arrayAdapter = new ArrayAdapter<>
                (inflater.getContext(), android.R.layout.simple_spinner_item, enumElements);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(arrayAdapter);
        spinner.setSelection(0);

        CustomField res = new CustomField(id, CustomField.FieldType.EnumType) {
            @Override
            public Object getValue() {
                if(spinner.getSelectedItem() == nullElement) {
                    return null;
                }

                return ((EnumerationElement)spinner.getSelectedItem()).getElementId();
            }

            @Override
            public void setValue(Object value) {
                if(value == null || value.equals("") || value.equals("null")) {
                    spinner.setSelection(0);
                } else {
                    int valIndex = -1;
                    for(int i = 0; i < enumElements.size(); i++) {
                        if(value.equals(enumElements.get(i).getElementId())) {
                            valIndex = i;
                            break;
                        }
                    }

                    if(valIndex < 0)
                    {
                        throw new InvalidValueException();
                    }

                    spinner.setSelection(valIndex);
                }
            }

            @Override
            public View getView() {
                return fieldView;
            }

            @Override
            public void setEnabled(boolean enabled) {
                spinner.setEnabled(enabled);
            }

            @Override
            public boolean isEnabled() {
                return spinner.isEnabled();
            }
        };

        res.setMaybeNull(maybeNull);
        return res;
    }

    public CustomField createADTField(String id, String adtId, String label, String helperText) {
        ADTCustomField adtField = new ADTCustomField(id, adtId, label, helperText, baseFragment);
        resultHandlerFields.add(adtField);
        return adtField;
    }

    public CustomField createListField(String id,String label, String helpText) {
        ListCustomField listField = new ListCustomField(id, label, helpText, baseFragment);
        resultHandlerFields.add(listField);
        return listField;
    }

    public CustomField createDateTimeField(String id, CustomField.FieldType type, String label, String helperText) {
        return new DateTimeCustomField(id, type, baseFragment, label, helperText);
    }

    public CustomField createFoodlistField(String id) {
        FoodlistCustomField foodlistField = new FoodlistCustomField(id, CustomField.FieldType.FoodList, baseFragment);
        resultHandlerFields.add(foodlistField);
        return foodlistField;
    }

    /* ----- */


    public CustomField createCustomFieldFromSchema(String id, JSONObject schema, boolean isNewField) throws JSONException {
        /* Parse Schema */
        String label = schema.getString("label");
//        if(isNewField) {
//            label += " " + getString(R.string.field_is_new_annotation);
//        }
        CustomField.FieldType type = CustomField.FieldType.valueOf(schema.getString("datatype"));

        String helpText = null;
        if(schema.has("helpText"))
            helpText = schema.getString("helpText");

        String sliderMinLabel = null;
        if(schema.has("sliderMinLabel"))
            sliderMinLabel = schema.getString("sliderMinLabel");

        String sliderMaxLabel = null;
        if(schema.has("sliderMaxLabel"))
            sliderMaxLabel = schema.getString("sliderMaxLabel");

        String unitString = null;
        if(schema.has("unitString"))
            unitString = schema.getString("unitString");

        String adtEnumID = null;
        if(schema.has("adt_enum_id"))
            adtEnumID = schema.getString("adt_enum_id");

        String elementsType = null;
        if(schema.has("elements_type"))
            elementsType = schema.getString("elements_type");

        Float minValue = null;
        if(schema.has("minValue"))
            minValue = Double.valueOf(schema.getDouble("minValue")).floatValue();

        Float maxValue = null;
        if(schema.has("maxValue"))
            maxValue = Double.valueOf(schema.getDouble("maxValue")).floatValue();

        float sliderStepSize = 0f;
        if(schema.has("sliderStepSize"))
            sliderStepSize = Double.valueOf(schema.getDouble("sliderStepSize")).floatValue();

        boolean maybeNull = schema.optBoolean("maybeNull");
        boolean qrCodeInput = schema.optBoolean("qrCodeInput");

        /* create custom field and view */
        CustomField res = null;

        if(schema.has("useSlider") && schema.getBoolean("useSlider")) {
            res =  createSlider(id, label, helpText, minValue, maxValue, sliderMinLabel, sliderMaxLabel, sliderStepSize);
        }
        if(res == null && (type == CustomField.FieldType.StringType || type == CustomField.FieldType.FloatType || type == CustomField.FieldType.IntType)) {
            int inputType = InputType.TYPE_CLASS_TEXT;

            if(type == CustomField.FieldType.FloatType)
                inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

            if(type == CustomField.FieldType.IntType)
                inputType = InputType.TYPE_CLASS_NUMBER;

            res =  createTextField(id, label, unitString, helpText, inputType, type, qrCodeInput);
        }
        if(res == null && type == CustomField.FieldType.EnumType && adtEnumID != null) {
            res =  createEnumSpinner(id, label, helpText, adtEnumID, maybeNull);
        }
        if(res == null && type == CustomField.FieldType.ADT && adtEnumID != null) {
            res =  createADTField(id, adtEnumID, label, helpText);
        }
        if(res == null && type == CustomField.FieldType.ListType && elementsType != null) {
            res =  createListField(id, label, helpText);
        }
        if(res == null && type == CustomField.FieldType.FoodList) {
            res =  createFoodlistField(id);
        }
        if(res == null && (type == CustomField.FieldType.DateType || type == CustomField.FieldType.TimeType)) {
            res =  createDateTimeField(id, type, label, helpText);
        }
        if(res == null && type == CustomField.FieldType.BoolType) {
            res =  createCheckbox(id, label, helpText);
        }

        if(res != null) {
            res.setMaybeNull(maybeNull);
            res.setMinValue(minValue);
            res.setMaxValue(maxValue);
        }

        return res;
    }


    /* ----- */

    public void handleActivityResultForADTFields(int requestCode, int resultCode, @Nullable Intent data) {
        for(CustomField field : resultHandlerFields) {
            field.handleActivityResult(requestCode, resultCode, data);
        }
    }
}
