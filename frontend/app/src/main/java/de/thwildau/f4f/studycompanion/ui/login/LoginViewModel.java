package de.thwildau.f4f.studycompanion.ui.login;

import static de.thwildau.f4f.studycompanion.qr.QRCodeGeneratorActivity.QR_PREFIX;
import static de.thwildau.f4f.studycompanion.qr.QRCodeGeneratorActivity.QR_TOKEN_KEY;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;

public class LoginViewModel extends ViewModel {
    enum LoginResultType {
        DOWNLOADING_STRUCTURES,
        SUCCESS,
        AUTHENTICATION_ERROR,
        CONNECTION_ERROR,
        INVALID_ENDPOINT,
        QR_EXPIRED,
    }

    private MutableLiveData<LoginFormState> loginFormState = new MutableLiveData<>();
    private MutableLiveData<LoginResultType> loginResult = new MutableLiveData<>();

    private MutableLiveData<String> selectedEndpoint = new MutableLiveData<>();

    public LoginViewModel() {
        SharedPreferences sp = StudyCompanion.getGlobalPreferences();

    }

    LiveData<LoginFormState> getLoginFormState() {
        return loginFormState;
    }

    LiveData<LoginResultType> getLoginResult() {
        return loginResult;
    }

    public List<String> getEndpointList() {
        ArrayList<String> res = new ArrayList<>();

        String[] presetLabels =
                StudyCompanion.isReleaseBuild() ?
                        new String[]{StudyCompanion.getAppContext().getResources().getString(R.string.server_name_release), "Custom"} :
                        StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_labels);

        String customUrl = StudyCompanion.getGlobalPreferences().getString("backend_custom_server", null);

        for (String presetLabel : presetLabels) {
            if (presetLabel.equalsIgnoreCase("custom")) {
                continue;
            }

            res.add(presetLabel);
        }

        if (customUrl != null) {
            res.add(customUrl);
        }

        return res;
    }

    public LiveData<String> getSelectedEndpoint() {

        if (selectedEndpoint.getValue() == null) {
            String endpoint = BackendIO.getServerUrl();

            String[] presetLabels;
            String[] presetUrls;

            if (StudyCompanion.isReleaseBuild()) {
                presetLabels = new String[]{StudyCompanion.getAppContext().getResources().getString(R.string.server_name_release)};
                presetUrls = new String[]{StudyCompanion.getAppContext().getResources().getString(R.string.server_endpoint_release), "Custom"};
            } else {
                presetLabels = StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_labels);
                presetUrls = StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_values);
            }


            for (int i = 0; i < presetLabels.length; i++) {
                String presetLabel = presetLabels[i];
                String presetUrl = presetUrls[i];

                if (endpoint.equals(presetUrl)) {
                    endpoint = presetLabel;
                    break;
                }
            }

            selectedEndpoint.setValue(endpoint);
        }

        return selectedEndpoint;
    }

    private void attemptLogin(String token) {
        BackendIO.fetchCurrentUserFromServer(new BackendIO.UserAuthenticationCallback() {
            @Override
            public void authenticationSuccess() {
                loginResult.setValue(LoginResultType.SUCCESS);
            }

            @Override
            public void authenticationError(AuthenticationErrorType errorType) {
                if (errorType == AuthenticationErrorType.AUTHENTICATION_ERROR)
                    loginResult.setValue(LoginResultType.QR_EXPIRED);
                else
                    loginResult.setValue(LoginResultType.CONNECTION_ERROR);
            }

            @Override
            public void onBeginDownloadStructures() {
                loginResult.setValue(LoginResultType.DOWNLOADING_STRUCTURES);
            }

        }, token, false);
    }

    public void login(String qrCodeData) {
        boolean validCode = true;
        // true, if encoded string meets the formal criteria.
        // Does not mean, it is actually a valid session token.
        // This will be verified on server side in next step.

        String token = "";
        String endpointUrl = null;

        if (URLUtil.isValidUrl(qrCodeData)) { // QR-Code is a URL (new version)

            // If the QR contains an URL, we check, if the specified host equals the host of the
            // APK download URL provided by the server device settings.
            // If this the case, we read the token from the query parameters.

            Uri qrUri = Uri.parse(qrCodeData);

            endpointUrl = String.format("%s://%s/", qrUri.getScheme(), qrUri.getAuthority());

            try {
                token = qrUri.getQueryParameter(QR_TOKEN_KEY);
                if (Utils.nullOrEmpty(token)) {
                    validCode = false;
                }
            } catch (Exception e) {
                validCode = false;
            }

        } else { // QR-Code is a prefixed String (old version)

            if (qrCodeData.length() < QR_PREFIX.length() + 1) {
                validCode = false;
            }

            if (validCode) {
                String prefix = qrCodeData.substring(0, QR_PREFIX.length());
                if (!prefix.equals(QR_PREFIX)) {
                    validCode = false;
                } else {
                    token = qrCodeData.substring(QR_PREFIX.length());
                }
            }
        }

        if (!validCode) {
            loginResult.setValue(LoginResultType.AUTHENTICATION_ERROR);
            return;
        }


        Log.d("Login", "Token: " + token);
        if (endpointUrl == null || BackendIO.getServerUrl().equals(endpointUrl)) {
            attemptLogin(token);
            return;
        }

        Log.d("Login", "Request change endpoint: " + endpointUrl);

        String finalToken = token;
        String finalEndpointUrl = endpointUrl;
        BackendIO.testServerUrl(endpointUrl, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject endpointTestResult) {
                BackendIO.setServer(finalEndpointUrl);
                attemptLogin(finalToken);
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                String errorMessageStr = "Request failed with status " + errorStatusCode + ".";
                if(errorMessage != null)
                    errorMessageStr += " " + errorMessage;

                Toast.makeText(StudyCompanion.getAppContext(), errorMessageStr, Toast.LENGTH_LONG).show();
                loginResult.setValue(LoginResultType.INVALID_ENDPOINT);
            }
        });
    }

    public void login(String endpoint, String username, String password) {

        BackendIO.testServerUrl(endpoint, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject endpointTestResult) {
                BackendIO.setServer(endpoint);
                BackendIO.authenticateUser(username, password, new BackendIO.UserAuthenticationCallback() {
                    @Override
                    public void authenticationSuccess() {
                        loginResult.setValue(LoginResultType.SUCCESS);
                    }

                    @Override
                    public void authenticationError(AuthenticationErrorType errorType) {
                        loginResult.setValue(LoginResultType.valueOf(errorType.name()));
                    }

                    @Override
                    public void onBeginDownloadStructures() {
                        loginResult.setValue(LoginResultType.DOWNLOADING_STRUCTURES);
                    }
                });
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                loginResult.setValue(LoginResultType.INVALID_ENDPOINT);
            }
        });


    }

    public void loginDataChanged(String endpoint, String username, String password) {
        boolean isValid = true;

        Integer usernameError = null;
        Integer passwordError = null;
        Integer endpointError = null;

        if (!isUserNameValid(username)) {
            usernameError = R.string.invalid_username;
            isValid = false;
        }
        if (!isPasswordValid(password)) {
            passwordError = R.string.invalid_password;
            isValid = false;
        }
        if (!isEndpointValid(endpoint)) {
            endpointError = R.string.invalid_endpoint;
            isValid = false;
        }

        if (isValid) {
            loginFormState.setValue(new LoginFormState(true));
        } else {
            loginFormState.setValue(new LoginFormState(endpointError, usernameError, passwordError));
        }
    }

    private boolean isUserNameValid(String username) {
        if (username == null) {
            return false;
        }

        return !username.trim().isEmpty();
    }

    private boolean isPasswordValid(String password) {
        return !Utils.nullOrEmpty(password);
    }

    private boolean isEndpointValid(String endpoint) {
        // Allow either a label of the pre-defined endpoints or a valid URL

        if (endpoint == null || endpoint.equalsIgnoreCase("custom")) {
            return false;
        }

        String[] endpointLabels =
                StudyCompanion.isReleaseBuild() ?
                        new String[]{StudyCompanion.getAppContext().getResources().getString(R.string.server_name_release), "Custom"} :
                        StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_labels);

        for (String endpointLabel : endpointLabels) {
            if (endpointLabel.equals(endpoint)) {
                return true;
            }
        }

        return URLUtil.isValidUrl(endpoint);
    }


}