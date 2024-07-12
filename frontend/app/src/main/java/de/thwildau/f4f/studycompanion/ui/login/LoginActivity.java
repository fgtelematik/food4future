package de.thwildau.f4f.studycompanion.ui.login;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.qrcode.QRCodeReader;

import org.w3c.dom.Text;

import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.qr.QRCodeReaderActivity;

public class LoginActivity extends AppCompatActivity {

    private LoginViewModel loginViewModel;

    private final static int QR_REQUEST = 1;
    private boolean initializingAutoText = true;

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton ;
    private ImageButton qrButton ;
    private TextView qrLabel;
    private TextView loginStatusLabel;
    private ProgressBar loadingProgressBar;
    private AutoCompleteTextView endpointEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        getSupportActionBar().setTitle(R.string.login_title);

        final TextView invalidTextView = findViewById(R.id.tvInvalidCredentials);

        endpointEditText = findViewById(R.id.endpoint);
        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.login);
        qrButton = findViewById(R.id.btnQR);
        qrLabel = findViewById(R.id.login_qr_label);
        loadingProgressBar = findViewById(R.id.loading);
        loginStatusLabel = findViewById(R.id.login_status_label);

        loginViewModel.getLoginFormState().observe(this, new Observer<LoginFormState>() {
            @Override
            public void onChanged(@Nullable LoginFormState loginFormState) {
                if (loginFormState == null || initializingAutoText) {
                    return;
                }
                loginButton.setEnabled(loginFormState.isDataValid());

                endpointEditText.setError(null);
                usernameEditText.setError(null);
                passwordEditText.setError(null);

                if (loginFormState.getEndpointError() != null) {
                    endpointEditText.setError(getString(loginFormState.getEndpointError()));
                }
                if (loginFormState.getUsernameError() != null) {
                    usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                    passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
            }
        });

        loginViewModel.getLoginResult().observe(this, loginResult -> {
            if (loginResult == null) {
                return;
            }

            if(loginResult == LoginViewModel.LoginResultType.DOWNLOADING_STRUCTURES) {
                loginStatusLabel.setText(R.string.login_status_downloading);
                return;
            }

            setBusy(false);

            if (loginResult == LoginViewModel.LoginResultType.SUCCESS) {
                setResult(Activity.RESULT_OK);
                Toast.makeText(LoginActivity.this, getString(R.string.login_successful_message, BackendIO.getCurrentUser().username), Toast.LENGTH_LONG).show();
                finish();
            } else {
                switch (loginResult) {
                    case AUTHENTICATION_ERROR:
                        invalidTextView.setText(R.string.login_authentication_error);
                        break;
                    case CONNECTION_ERROR:
                        invalidTextView.setText(R.string.login_connection_error);
                        break;
                    case INVALID_ENDPOINT:
                        invalidTextView.setText(R.string.login_invalid_endpoint);
                        break;
                    case QR_EXPIRED:
                        invalidTextView.setText(R.string.login_qr_expired);
                        break;

                }
                invalidTextView.setVisibility(View.VISIBLE);
            }
        });

        loginViewModel.getSelectedEndpoint().observe(this, endpoint -> {
            if (endpoint == null)
                endpoint = "";

            endpointEditText.setText(endpoint);
            initializingAutoText = false;
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(usernameEditText.getText().toString().equals("crashme")) {
                    throw new RuntimeException("Simulated App Crash");
                }
                invalidTextView.setVisibility(View.INVISIBLE);
                loginViewModel.loginDataChanged(
                        endpointEditText.getText().toString(),
                        usernameEditText.getText().toString(),
                        passwordEditText.getText().toString());
            }
        };
        endpointEditText.setThreshold(0);
        endpointEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if(hasFocus) {
                endpointEditText.showDropDown();
            }
        });
        endpointEditText.setOnClickListener(v -> {
            endpointEditText.showDropDown();
        });

        endpointEditText.setOnItemClickListener((adapterView, view, i, l) -> loginViewModel.loginDataChanged(
                endpointEditText.getText().toString(),
                usernameEditText.getText().toString(),
                passwordEditText.getText().toString())
        );

        endpointEditText.addTextChangedListener(afterTextChangedListener);
        usernameEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.addTextChangedListener(afterTextChangedListener);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    setBusy(true);
                    loginViewModel.login(
                            endpointEditText.getText().toString(),
                            usernameEditText.getText().toString(),
                            passwordEditText.getText().toString());
                }
                return false;
            }
        });

        final ArrayAdapter<String> endpointAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, loginViewModel.getEndpointList()) {
            final List<String> items =  loginViewModel.getEndpointList();

            // Implement custom filter to ALWAYS show all suggested endpoints, independent of the current entered text
            private final Filter alwaysShowAllFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = items;
                    filterResults.count = items.size();
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence charSequence, FilterResults filterResults) {

                }
            };

            @NonNull
            @Override
            public Filter getFilter() {
                return alwaysShowAllFilter;
            }
        };

        endpointEditText.setAdapter(endpointAdapter);

        loginButton.setOnClickListener(v -> {
            setBusy(true);
            loginViewModel.login(
                    endpointEditText.getText().toString(),
                    usernameEditText.getText().toString(),
                    passwordEditText.getText().toString());
        });

        qrButton.setOnClickListener(view -> {
            Intent i = new Intent(LoginActivity.this, QRCodeReaderActivity.class);
            i.putExtra(QRCodeReaderActivity.EXTRA_INFOTEXT, getString(R.string.qr_helptext_login));
            startActivityForResult(i, QR_REQUEST);
        });
    }

    private void setBusy(boolean busy) {
        loadingProgressBar.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        loginStatusLabel.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        loginButton.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        qrButton.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        qrLabel.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        passwordEditText.setEnabled(!busy);
        usernameEditText.setEnabled(!busy);
        endpointEditText.setEnabled(!busy);

        if(busy)
            loginStatusLabel.setText(R.string.login_status_authenticating);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_REQUEST && resultCode == Activity.RESULT_OK) {
            String qrData = data.getStringExtra(QRCodeReaderActivity.EXTRA_BARCODE_DATA);
            setBusy(true);
            loginViewModel.login(qrData);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Make back arrow return to previous activity
        setResult(Activity.RESULT_CANCELED);
        onBackPressed();
        return true;
    }
}