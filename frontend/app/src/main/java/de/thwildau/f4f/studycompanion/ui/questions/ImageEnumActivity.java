package de.thwildau.f4f.studycompanion.ui.questions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.EnumerationElement;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.qr.QRCodeReaderActivity;

public class ImageEnumActivity extends AppCompatActivity {

    private enum OthersType {
        PIECES,
        MILLILITERS,
        WEIGHT,
        LABEL,
        BARCODE
    }


    private static final String PREFIX_OTHERS_ENTRY = "others";
    private static final String OTHERS_TYPE_SEPARATOR = "$";

    private static final int BARCODE_REQUEST_CODE = 111;

    /* Extras for calling intent */
    public static final String EXTRA_ENUM_ID = "EXTRA_DATA";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_INFOTEXT = "EXTRA_INFOTEXT";

    /* Extras for resulting Intent */
    public static final String EXTRA_SELECTED_ENTRY = "EXTRA_SELECTED_ENTRY";
    public static final String EXTRA_OTHERS_STRING_VALUE = "EXTRA_OTHERS_STRING_VALUE";
    public static final String EXTRA_OTHERS_NUMBER_VALUE = "EXTRA_OTHERS_NUMBER_VALUE";
    public static final String EXTRA_OTHERS_NUMBER_UNIT_STRING = "EXTRA_OTHERS_NUMBER_UNIT_STRING";

    private TableLayout gridTable;
    private List<EnumerationElement> enumElements;

    private Map<View, String> entryButtons;

    private void prepareButtonGrid() {
        entryButtons = new HashMap<>();
        TableRow currentRow = null;
        int i = 0;

        gridTable.removeAllViews();

        if(enumElements == null) {
            return;
        }

        for(EnumerationElement enumElement : enumElements) {
            // Populate a new table row for each two entries

            if(i % 2 == 0) {
                currentRow = new TableRow(this);
            }

            View elementView = null;
            View elementButton = null;
            String label = enumElement.getLabel();
            String elementId = enumElement.getElementId();



            Drawable elementDrawable = enumElement.getDrawable(this);
            if(elementDrawable != null) {

                elementView = getLayoutInflater().inflate(R.layout.content_image_enum_entry, null);
                TextView elementLabelView = elementView.findViewById(R.id.tvLabel);
                elementLabelView.setText(label);

                ImageButton elementImageButton = elementView.findViewById(R.id.image_button);
                elementImageButton.setImageDrawable(elementDrawable);
                elementButton = elementImageButton;

            } else {
                elementView = getLayoutInflater().inflate(R.layout.content_image_enum_entry_noimage, null);
                Button tmpElementButton = elementView.findViewById(R.id.text_button);
                tmpElementButton.setText(label);

                elementButton = tmpElementButton;
            }

            TableRow.LayoutParams cellLayoutParams = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            cellLayoutParams.setMargins(15,0,15,0);
            currentRow.addView(elementView, cellLayoutParams);

            OthersType othersType = null;
            if(elementId.startsWith(PREFIX_OTHERS_ENTRY)) {
                try {
                    String othersTypeStr = elementId.split(Pattern.quote(OTHERS_TYPE_SEPARATOR))[1]; // may throw IndexOutOfBoundsException
                    othersType = OthersType.valueOf(othersTypeStr.toUpperCase()); // may throw IllegalArgumentException
                } catch(Throwable e) {
                    // no valid "others" entry.
                }
            }


            if(othersType != null) {
                OthersType finalOthersType = othersType;
                elementButton.setOnClickListener((v) -> { onOthersButtonClick(v, finalOthersType, label); });
            } else {
                elementButton.setOnClickListener((v) -> {confirmSelection(elementId, null, null);});
            }


            entryButtons.put(elementButton, elementId);


            if(i % 2 == 1) {
                gridTable.addView(currentRow);
            }

            i++;
        }

        if(i % 2 == 1) {
            // total number of entries is uneven. Add the last remaining cell and fill the space with a Space view
            gridTable.addView(currentRow);
            View placeholder = new Space(this);
            placeholder.setLayoutParams(new TableRow.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            currentRow.addView(placeholder);
        }

    }

    private void confirmSelection(String elementId, Object othersValue, String othersUnitString) {
        Intent i = new Intent();
        if(othersValue != null) {
            if(othersValue instanceof Double) {
                i.putExtra(EXTRA_OTHERS_NUMBER_VALUE, (double)othersValue);
            } else if(othersValue instanceof String) {
                i.putExtra(EXTRA_OTHERS_STRING_VALUE, (String)othersValue);
            }

            if(othersUnitString != null) {
                i.putExtra(EXTRA_OTHERS_NUMBER_UNIT_STRING, othersUnitString);
            }
        }
        i.putExtra(EXTRA_SELECTED_ENTRY, elementId);
        setResult(Activity.RESULT_OK, i);
        finish();
    }


    private void openBarcodeReader(String elementId) {
        Intent i = new Intent(this, QRCodeReaderActivity.class);
        i.putExtra(QRCodeReaderActivity.EXTRA_BARCODE_MODE, true);
        i.putExtra(QRCodeReaderActivity.EXTRA_TITLE, getString(R.string.qr_title_barcode));
        i.putExtra(QRCodeReaderActivity.EXTRA_INFOTEXT, getString(R.string.qr_helptext_barcode));
        startActivityForResult(i, 111);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != BARCODE_REQUEST_CODE || resultCode != RESULT_OK || data == null || !data.hasExtra(QRCodeReaderActivity.EXTRA_BARCODE_DATA))
            return;

        String value = data.getStringExtra(QRCodeReaderActivity.EXTRA_BARCODE_DATA);
        String barcodeFormat = data.getStringExtra(QRCodeReaderActivity.EXTRA_BARCODE_FORMAT);

        String stringValue = barcodeFormat + ": " + value;
        String elementId = PREFIX_OTHERS_ENTRY + OTHERS_TYPE_SEPARATOR + OthersType.BARCODE.toString().toLowerCase();

        confirmSelection(elementId, stringValue, null);
    }

    private void onOthersButtonClick(View v, OthersType othersType, String label) {
        final String elementId = entryButtons.get(v);

        if(othersType == OthersType.BARCODE) {
            // Barcode reader opens in extra Activity
            openBarcodeReader(elementId);
            return;
        }

        // In all other types of "Other"-Field, we remain in this activity and open a dialog.

        int unitStrResource = 0;
        String unitStr = null;
        String message = "";
        final boolean stringType;
        switch(othersType) {
            case PIECES:
                unitStrResource = R.string.enum_unit_pieces;
                message = getString(R.string.enum_unit_pieces_text);
                stringType = false;
                break;
            case WEIGHT:
                unitStrResource = R.string.enum_unit_gramms;
                unitStr = "g";
                message = getString(R.string.enum_unit_gramms_text);
                stringType = false;
                break;
            case MILLILITERS:
                unitStrResource = R.string.enum_unit_milliliters;
                unitStr = "ml";
                message = getString(R.string.enum_unit_milliliters_text);
                stringType = false;
                break;
            case LABEL:
                message = getString(R.string.enum_unit_label_text);
                stringType = true;
                break;
            default:
                // default case was actually excluded before method call, only include it
                // for setting final stringType and avoid compiler error
                stringType = false;
        }

        final View inputForm;

        if(stringType) {
            inputForm = getLayoutInflater().inflate(R.layout.custom_field_text, null);
            inputForm.findViewById(R.id.btnQR).setVisibility(View.GONE); // hide QR button
        } else {
            inputForm = getLayoutInflater().inflate(R.layout.custom_field_text_with_unit, null);
            final TextView unitText = inputForm.findViewById(R.id.valTextUnit);
            unitText.setText(unitStrResource);
        }


        final TextView messageText = inputForm.findViewById(R.id.valTextLabel);

        final TextView helpText = inputForm.findViewById(R.id.valTextHelp);
        helpText.setVisibility(View.GONE);

        final TextInputLayout valueInputLayout = inputForm.findViewById(stringType ? R.id.valText : R.id.valTextWithUnit);
        final TextInputEditText valueInput =  inputForm.findViewById(R.id.valTextValue);

        final String unitStrFinal = unitStr;

        inputForm.setPadding(50, 10, 50, 10);

        valueInput.setInputType(stringType ? InputType.TYPE_CLASS_TEXT : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        valueInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                valueInputLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });


        messageText.setText(message);

        final AlertDialog dialog =  new AlertDialog.Builder(this)
                .setTitle(label)
                .setView(inputForm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, null) // OnClickListener is be added below
                .create();

        dialog.setOnShowListener((dv) -> {
            // manually edit the OK button click listener to avoid auto-closing dialog when user did not enter a valid value
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener((buttonview) -> {
                String valueString = valueInput.getText().toString();

                boolean illegalValue = false;
                Double doubleValue = null;
                if(valueString.isEmpty()) {
                    illegalValue = true;
                } else if(!stringType) {
                    try {
                         doubleValue = Double.parseDouble(valueString);
                    } catch (NumberFormatException e) {
                        illegalValue = true;
                    }
                }

                if(illegalValue) {
                    valueInputLayout.setErrorEnabled(true);
                    valueInputLayout.setError(getString(R.string.enum_others_invalid_text));
                } else {
                    dialog.dismiss();
                    confirmSelection(elementId, stringType ? valueString : doubleValue, unitStrFinal);
                }

            });
        });

        valueInput.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure Toolbar, set Back Button and Title
        setContentView(R.layout.activity_image_enum);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String helpText = getIntent().getStringExtra(EXTRA_INFOTEXT);

        TextView textHelpText = findViewById(R.id.imageEnumHelpText);
        if(!Utils.nullOrEmpty(helpText)) {
            textHelpText.setVisibility(View.VISIBLE);
            textHelpText.setText(helpText);
        } else {
            textHelpText.setVisibility(View.GONE);
        }

        if(title != null) {
            getSupportActionBar().setTitle(title);
        }

        gridTable = findViewById(R.id.grid_table);
        String enumId = getIntent().getStringExtra(EXTRA_ENUM_ID);

        enumElements = SchemaProvider.getEnumElements(enumId);

        TextView textEnumError = findViewById(R.id.imageEnumErrorText);
        textEnumError.setVisibility(enumElements == null || enumElements.isEmpty() ? View.VISIBLE : View.GONE);

        prepareButtonGrid();

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}