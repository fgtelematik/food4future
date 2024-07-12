package de.thwildau.f4f.studycompanion.ui.customform;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;

import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;

public class DateTimeCustomField extends CustomField implements OpensExtraActivity {
    private ImageButton buttonEdit;
    private ImageButton buttonClear;
    private View fieldView;
    private Fragment baseFragment;
    private Date mValue;
    private TextView textValue;
    private String label;

    public DateTimeCustomField(String id, FieldType fieldType, Fragment baseFragment, String label, String helperText) {
        super(id, fieldType);
        this.baseFragment = baseFragment;
        this.label = label;
        fieldView = baseFragment.getLayoutInflater().inflate(R.layout.custom_field_date_time, null);

        if(fieldType == FieldType.TimeType) {
            fieldView.findViewById(R.id.valDateBtnEditDate).setVisibility(View.GONE);
            buttonEdit = fieldView.findViewById(R.id.valDateBtnEditTime);
            buttonEdit.setVisibility(View.VISIBLE);
        } else if(fieldType == FieldType.DateType) {
            fieldView.findViewById(R.id.valDateBtnEditTime).setVisibility(View.GONE);
            buttonEdit = fieldView.findViewById(R.id.valDateBtnEditDate);
            buttonEdit.setVisibility(View.VISIBLE);
        } else {
            throw new IllegalStateException("DateTimeCustomField can only be used for TimeType or DateType field types.");
        }

        textValue = fieldView.findViewById(R.id.valDateValue);

        buttonEdit.setOnClickListener(this::showDateTimeDialog);

        buttonClear = fieldView.findViewById(R.id.valDateBtnClear);
        buttonClear.setVisibility(View.GONE);
        buttonClear.setOnClickListener(view -> setValue(null));

        ((TextView)fieldView.findViewById(R.id.valDateLabel)).setText(label);

        TextView textHelper = fieldView.findViewById(R.id.valDateHelper);
        if(helperText != null) {
            textHelper.setText(helperText);
        } else {
            textHelper.setVisibility(View.GONE);
        }

        setValue(null);
    }

    @Override
    public void setMaybeNull(boolean maybeNull) {
        super.setMaybeNull(maybeNull);
        buttonClear.setVisibility(maybeNull ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openExtraActivity() {
        showDateTimeDialog(null);
    }

    private static Calendar getCalInstanceForTime(int hour, int minute, int second, boolean localized) {
        Calendar cal = localized ?  Calendar.getInstance() : Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.YEAR, 1900);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return cal;
    }

    private static Calendar getCalInstanceForDay(int year, int month, int day) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        return cal;
    }


    private void showDateTimeDialog(View v) {
        Calendar initDate = Calendar.getInstance();

        if(mValue != null) {
            initDate.setTime((Date)mValue);
        }

        AppCompatDialogFragment dialog = null;

        switch(getType()) {
            case TimeType:
                dialog = TimePickerDialog.newInstance(
                        (view, hourOfDay, minute, second) -> {
                            Calendar cal = getCalInstanceForTime(hourOfDay, minute, 0, true);
                            setValue(cal.getTime());
                        },
                        initDate.get(Calendar.HOUR_OF_DAY),
                        initDate.get(Calendar.MINUTE),
                        initDate.get(Calendar.SECOND),
                        true
                );
                break;

            case DateType:
                dialog = DatePickerDialog.newInstance(
                        (view, year, monthOfYear, dayOfMonth) -> {
                            Calendar cal = getCalInstanceForDay(year, monthOfYear, dayOfMonth);
                            setValue(cal.getTime());
                        },
                        initDate.get(Calendar.YEAR), // Initial year selection
                        initDate.get(Calendar.MONTH), // Initial month selection
                        initDate.get(Calendar.DAY_OF_MONTH) // Inital day selection
                );
        }

        dialog.show(baseFragment.getActivity().getSupportFragmentManager(), getId());
    }


    @Override
    public Object getValue() {
        if(mValue == null)
            return null;
        else
            return Utils.getServerTimeFormat().format(mValue);
    }

    private static Integer tryExtractOffsetFromDefaultString(String defaultString) {
        Pattern pattern = Pattern.compile("(now|today)([\\+\\-])(\\d+)");
        Matcher matcher =  pattern.matcher(defaultString);

        if(!matcher.find())
            // no offset specified
            return null;

        String sign = matcher.group(2);
        if(sign == null) return null;
        int factor = sign.equals("+") ? 1 : -1;

        String offsetStr = matcher.group(3);
        try {
            int offset = Integer.parseInt(offsetStr);
            return factor * offset;
        } catch (NumberFormatException e) {
            // this should actually be excluded by pattern
        }

        return null;
    }

    @Override
    public void setValue(Object value) {
        if (Utils.nullOrEmpty(value)) {
            mValue = null;
        } else if (value instanceof Date) {
            mValue = (Date) value;
        } else {
            Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("UTC")); // UTC Calendar instance with current Date/Time

            String valStr = value.toString();

            if(valStr.startsWith("today")) {
                Calendar cal = getCalInstanceForDay(calNow.get(Calendar.YEAR), calNow.get(Calendar.MONTH), calNow.get(Calendar.DAY_OF_MONTH));
                Integer dayOffset = tryExtractOffsetFromDefaultString(valStr);
                if(dayOffset != null) {
                    cal.add(Calendar.DATE, dayOffset);
                }

                mValue = cal.getTime();

            } else if(valStr.startsWith("now"))  {
                Calendar cal = getCalInstanceForTime(calNow.get(Calendar.HOUR_OF_DAY), calNow.get(Calendar.MINUTE), calNow.get(Calendar.SECOND), false);
                Integer secondsOffset = tryExtractOffsetFromDefaultString(valStr);
                if (secondsOffset != null) {
                    cal.add(Calendar.SECOND, secondsOffset);
                }

                mValue = cal.getTime();

            } else {
                try {
                    mValue = Utils.getServerTimeFormat().parse(valStr);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }

        buttonClear.setEnabled(buttonEdit.isEnabled() && (mValue != null));

        if(mValue != null) {
            String dateString = "";
            switch(getType()) {
                case TimeType:
                    dateString = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(mValue);
                    break;
                case DateType:
                    dateString = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault()).format(mValue);
            }
            textValue.setText(dateString);
        } else {
            textValue.setText(R.string.value_not_available);
        }

    }

    @Override
    public View getView() {
        return fieldView;
    }

    @Override
    public void setEnabled(boolean enabled) {
        buttonEdit.setEnabled(enabled);
        buttonClear.setEnabled(mValue == null ? false : enabled);
    }

    @Override
    public boolean isEnabled() {
        return buttonEdit.isEnabled();
    }
}
