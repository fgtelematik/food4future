package de.thwildau.f4f.studycompanion.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.thwildau.f4f.studycompanion.BuildConfig;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;

public class BackendIO {

    // This increased timeout is currently only used for DataSync send requests.
    private static final int INCREASED_REQUEST_TIMEOUT_MS = 30000;

    private static final String SERVER_API_NAME = "f4f-server";
    private static final int MIN_SERVER_API_VERSION = 1;

    public interface UserAuthenticationCallback {
        enum AuthenticationErrorType {
            AUTHENTICATION_ERROR,
            CONNECTION_ERROR
        }

        /**
         * Gets called when authentication successfully succeeded.
         * The authenticated user is now logged in and the session token will be stored in preferences.
         */
        void authenticationSuccess();

        /**
         * If the authentication download fails.
         *
         * If this is called AFTER onBeginnDownloadStructures(), this implies an unexpected
         * error on server side (bug in server backend or incompatible App-Backend commit state)
         */
        void authenticationError(AuthenticationErrorType errorType);

        /**
         * The authentication was successful and the config/schema/food images for the study
         * associated to this user is being downloaded to the device.
         */
        void onBeginDownloadStructures();
    }

    public interface UserLoginStatusObserver {
        void isLoggedIn(User user);

        void isLoggedOut();
    }

    private class Endpoints {
        static final String LOGIN = "token";
        static final String GET_CURRENT_USER = "me";
        static final String LOGOUT = "logout";
        static final String INFO = "info";
    }


    public enum RemoteDatasetType {
        SCHEMAS("schemas"),
        ENUMS("enums"),
        ADTS("adts"),
        USERS("users"),
        USER("user"),
        TOKEN("token"),
        SYNC("sync"),
        CONFIG("config"),
        LOG("log"),
        SENDMAIL("sendmail");


        private String endpoint;

        RemoteDatasetType(String envUrl) {
            this.endpoint = envUrl;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }


    public interface RemoteRequestCompletedCallback {
        void onResponse(JSONObject response);

        void onError(int errorStatusCode, String errorMessage);
    }


    private static final String LOG_TAG = "BackendIO";
    private static final String ANON_KEY = "SQD3ib67ttxvkSpln2K7cw"; // as defined in spec, must be equal on server-side


    private static RequestQueue requestQueue = null;
    private static User currentUser = null;
    private static String currentAuthToken = null;
    private static ArrayList<UserLoginStatusObserver> userLoginStatusObservers = new ArrayList<>();
    private static SharedPreferences sharedPreferences;


    /**
     * An JsonObjectRequest, which already includes the current user's authentication token
     * allowing accessing restricted server endpoints
     */
    private static class JsonObjectAuthRequest extends JsonObjectRequest {
        private String customToken = null;
        private boolean authorizationRequired = true;

        public JsonObjectAuthRequest(int method, String url, @Nullable JSONObject jsonRequest, Response.Listener<JSONObject> listener, @Nullable Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
        }

        public JsonObjectAuthRequest(String url, @Nullable JSONObject jsonRequest, Response.Listener<JSONObject> listener, @Nullable Response.ErrorListener errorListener) {
            super(url, jsonRequest, listener, errorListener);
        }

        public void setCustomToken(String customToken) {
            this.customToken = customToken;
        }

        public void setAuthorizationRequired(boolean authorizationRequired) {
            this.authorizationRequired = authorizationRequired;
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            String token = customToken != null ? customToken : currentAuthToken;
            Map<String, String> headers = new HashMap<>();
            if ((null == token || token.isEmpty())) {
                // Auth token not available:
                if (authorizationRequired) {
                    throw new AuthFailureError("Authorization required.");
                }
            } else {
                // Auth token available:
                headers.put("Authorization", "Bearer " + token);
            }

            return headers;
        }
    }

    public static String getServerUrl() {
        Context appContext = StudyCompanion.getAppContext();
        boolean isReleaseBuild = StudyCompanion.isReleaseBuild();
        String defaultServer = appContext.getString(isReleaseBuild ? R.string.server_endpoint_release : R.string.server_endpoint_debug);

        // Set default value now for backend server if not set.
        // It's not best solution to do a write-op here, but I didn't find a better way to
        // force displaying the initial value in the Settings Vie, without hard-coding it as defaultValue in the XML
        // which would overwrite the default value in release build.
        if(sharedPreferences.getString("backend_server", "").equals("")) {
            sharedPreferences.edit().putString("backend_server", defaultServer).apply();
        }

        String res = sharedPreferences.getString("backend_server", defaultServer);

        if (res.equals("custom")) {
            res = sharedPreferences.getString("backend_custom_server", appContext.getString(R.string.server_endpoint_debug));
        }

        return res;
    }

    /**
     * Test if the input is a name of a endpoint URL preset.
     *
     * @param urlOrName A name of a Backend URL preset or a URL.
     * @return The URL of the preset or null if the input is not a preset name.
     */
    private static String isPresetUrl(String urlOrName) {
        String res = null;
        String url = urlOrName;

        if (!url.endsWith("/"))
            url += "/";

        String[] presetLabels;
        String[] presetUrls;

        if(StudyCompanion.isReleaseBuild()) {
            presetLabels = new String[] {StudyCompanion.getAppContext().getResources().getString(R.string.server_name_release)};
            presetUrls = new String[] {StudyCompanion.getAppContext().getResources().getString(R.string.server_endpoint_release), "Custom"};
        } else {
            presetLabels = StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_labels);
            presetUrls = StudyCompanion.getAppContext().getResources().getStringArray(R.array.server_preset_values);
        }


        for (int i = 0; i < presetLabels.length; i++) {
            String presetLabel = presetLabels[i];
            String presetUrl = presetUrls[i];

            if (presetLabel.equalsIgnoreCase("custom"))
                continue;

            if (presetLabel.equals(urlOrName) || presetUrl.equals(url)) {
                res = presetUrl;
                break;
            }
        }

        return res;
    }

    /**
     * Change the URL of the backend server API.
     * <p>
     * Warning: No check for valid endpoint URL.
     *
     * @param urlOrName Can either be a URL or a preset name.
     */
    public static void setServer(String urlOrName) {
        String presetUrl = isPresetUrl(urlOrName);

        if (presetUrl != null)
            sharedPreferences.edit().putString("backend_server", presetUrl).apply();
        else {
            if (!urlOrName.endsWith("/"))
                urlOrName += "/";

            sharedPreferences.edit().putString("backend_server", "custom").apply();
            sharedPreferences.edit().putString("backend_custom_server", urlOrName).apply();
        }

    }

    public static void testServerUrl(String urlOrName, RemoteRequestCompletedCallback callback) {
        String url = urlOrName;

        String presetUrl = isPresetUrl(urlOrName);
        if (presetUrl != null)
            url = presetUrl;
        else if (!url.endsWith("/"))
            url += "/";

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url + Endpoints.INFO, null, (JSONObject response) -> {
            String apiName = response.optString("api_name", "");
            int apiVersion = response.optInt("api_version", -1);
            int minAppVersion = response.optInt("min_android_app_version", Integer.MAX_VALUE);

            if (!apiName.equals(SERVER_API_NAME))
                callback.onError(0, "This is not a valid f4f backend service.");
            else if (apiVersion < MIN_SERVER_API_VERSION)
                callback.onError(0, "Server API version is too old.");
            else if (minAppVersion > BuildConfig.VERSION_CODE)
                callback.onError(0, "You need to update the app to proceed.");
            else
                callback.onResponse(response);


        }, error -> {
            int statusCode = -1;
            if (error.networkResponse != null)
                statusCode = error.networkResponse.statusCode;

            callback.onError(statusCode, error.getMessage());
        });

        requestQueue.add(request);
    }

    public static void initialize(Context context) {
        if (requestQueue == null) {
            // create one app-wide shared queue for handling backend server requests
            requestQueue = Volley.newRequestQueue(context);
        }

        // Read auth token from preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String token = sharedPreferences.getString(context.getResources().getString(R.string.backendAuthToken), null);

        currentAuthToken = token;

        restoreLocalUser(); // offline-login with locally cached User data, if available

        if (token != null) {
            // Try to auto-login, or auto log-out, if server session expired/removed!
            // All registered userLoginStatusObservers will be notified.
            fetchCurrentUserFromServer(null);
        }
    }


    private static void notifyLoginState(User lastUser) {

        boolean userHasChanged = currentUser == null && lastUser != null || // was not logged in, is logged in now
                currentUser != null && lastUser == null || // was logged in, has logged out
                currentUser != null && (!currentUser.id.equals(lastUser.id) || !currentUser.role.equals(lastUser.role));
                     // was logged with a different user id or user role has changed

        if(!userHasChanged)
            return;

        Context context = StudyCompanion.getAppContext();
        Handler handler = new Handler(context.getMainLooper());
        if (currentUser == null) {
            for (final UserLoginStatusObserver observer : userLoginStatusObservers) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        observer.isLoggedOut();
                    }
                });
            }
        } else {
            for (final UserLoginStatusObserver observer : userLoginStatusObservers) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        observer.isLoggedIn(currentUser);
                    }
                });
            }
        }
    }

    private static void restoreLocalUser() {
        Context context = StudyCompanion.getAppContext();
        String prefName = context.getResources().getString(R.string.currentUserJson);
        String userJson = sharedPreferences.getString(prefName, "");
        if (!Utils.nullOrEmpty(userJson)) {
            try {
                JSONObject userJsonObj = new JSONObject(userJson);
                currentUser = convertJsonToUserObject(userJsonObj);
                notifyLoginState(null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Store data of currently logged in user on device.
     */
    private static void storeLocalUser(JSONObject userData) {
        Context context = StudyCompanion.getAppContext();
        SharedPreferences.Editor spedit = sharedPreferences.edit();
        String prefName = context.getResources().getString(R.string.currentUserJson);
        if (userData == null) {
            spedit.remove(prefName);
        } else {
            spedit.putString(prefName, userData.toString());
        }
        spedit.apply();
    }

    private static void storeCurrentToken() {
        Context context = StudyCompanion.getAppContext();
        SharedPreferences.Editor spedit = sharedPreferences.edit();
        String prefName = context.getResources().getString(R.string.backendAuthToken);
        if (currentAuthToken == null) {
            spedit.remove(prefName);
        } else {
            spedit.putString(prefName, currentAuthToken);
        }
        spedit.apply();
    }

    public static boolean removeUserLoginStatusObserver(UserLoginStatusObserver observer) {
        return userLoginStatusObservers.remove(observer);
    }


    public static boolean addUserLoginStatusObserver(final UserLoginStatusObserver observer) {
        if (currentUser == null) {
            observer.isLoggedOut();
        } else {
            observer.isLoggedIn(currentUser);
        }
        return userLoginStatusObservers.add(observer);
    }

    /**
     * With this method, we can check, if the user is currently logged in and access the
     * current user's information.
     *
     * @return An instance of the User model of the current user, if user is logged in.
     * Null, if the user is not logged in.
     */
    public static User getCurrentUser() {
        return currentUser;
    }


    /**
     * Log out user synchronously.
     */
    public static void logout() {
        User lastUser = currentUser;
        currentUser = null;

        JsonObjectAuthRequest jsonObjectRequest = new JsonObjectAuthRequest
                (
                        Request.Method.GET,
                        getServerUrl() + Endpoints.LOGOUT,
                        null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                // we ignore, if logout was successful on server-side or not,
                                // user gets logged out on client anyways
                            }
                        }, null
                );
        jsonObjectRequest.setCustomToken(currentAuthToken); // use the last known token for this request before forgetting it
        currentAuthToken = null;

        storeCurrentToken();
        storeLocalUser(null);

        notifyLoginState(lastUser); // inform the login status observers

        addRequest(jsonObjectRequest);
    }


    private static User convertJsonToUserObject(JSONObject userJson) throws JSONException {
        User user = new User();
        user.role = Role.valueOf(userJson.getString("role"));
        user.username = userJson.getString("username");
        user.id = userJson.getString("id");
        if (userJson.has("anamnesis_data")) {
            try {
                JSONObject anamnesis_data = userJson.optJSONObject("anamnesis_data");
                if (anamnesis_data != null) {
                    if (anamnesis_data.has("first_names")) {
                        user.firstName = anamnesis_data.getString("first_names").split(" ")[0];
                    }
                    if (anamnesis_data.has("last_name")) {
                        user.lastName = anamnesis_data.getString("last_name");
                    }
                    user.anamnesisData = anamnesis_data.toString();
                }

            } catch (JSONException e) {
                Log.w(LOG_TAG, "Error evaluating anamnesis data for user: " + user.username);
                e.printStackTrace();
            }
        }
        return user;
    }

    private static void fetchCurrentUserFromServer(final UserAuthenticationCallback authenticationCallback) {
        fetchCurrentUserFromServer(authenticationCallback, null, true);
    }

    public static void fetchCurrentUserFromServer(final UserAuthenticationCallback authenticationCallback, String customAuthToken, boolean logoutOnAuthError) {

        JsonObjectAuthRequest authRequest = new JsonObjectAuthRequest
                (
                        Request.Method.GET,
                        getServerUrl() + Endpoints.GET_CURRENT_USER,
                        null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {

                                try {
                                    User lastUser = currentUser;
                                    currentUser = convertJsonToUserObject(response);

                                    if (customAuthToken != null) {
                                        currentAuthToken = customAuthToken;
                                    }

                                    // Store authentication data in device preferences:
                                    storeCurrentToken();
                                    storeLocalUser(response);

                                    // Before notifying any callbacks, we first make sure that current structures
                                    // (=schemas and config)) are locally available, since they are needed
                                    // by multiple parts of the UI when a user is logged in:
                                    // 1. With new API we make the config/schemas user dependent (depending of the study he/she is associated to)
                                    //    so the cached structures might not be appropriate for the new logged in user
                                    // 2. If this is the first time the user logs in since App install, there are no
                                    //    structures in the cache yet which would cause NullPointerExceptions by
                                    //    the UI parts that expect them to be available making the App permanently
                                    //    crash after login.
                                    if (currentUser != null) {
                                        if (authenticationCallback != null)
                                            authenticationCallback.onBeginDownloadStructures();

                                        SchemaProvider.downloadSturcturesFromServer((withError, configUpdated) -> {
                                            if (withError) {
                                                // problems in fetching the structures from server
                                                // either a bug in server backend app or incompatible API version
                                                // => we won't let the user log in and directly log out again
                                                serverLog(Log.ERROR, "BackendIO", "Unexpected Error while acquiring config/schema after login. THIS NEEDS INVESTIGATION, otherwise this user will not able to log in!");
                                                logout();
                                                Toast.makeText(StudyCompanion.getAppContext(), R.string.message_login_error, Toast.LENGTH_LONG).show();
                                            } else if (configUpdated) {

//                                                if (StudyCompanion.getGarminSensorManager().getObservableSdkInitializationError().getValue()) {
//                                                    // Garmin SDK was not initialized yet, maybe the SDK key was not yet present,
//                                                    // so try again.
//                                                    StudyCompanion.getGarminSensorManager().init();
//                                                    StudyCompanion.getGarminSensorManager().start();
//                                                }

                                                // This will only be the case when the device configuration was updated on server
                                                // since the last time schemas were downloaded.
                                                StudyCompanion.updateSensorConfig();
                                            }

                                            if (authenticationCallback != null) {
                                                if (withError)
                                                    authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.CONNECTION_ERROR);
                                                else
                                                    authenticationCallback.authenticationSuccess();
                                            }

                                            // after login, notify user login observers after schemas were updated
                                            notifyLoginState(lastUser);
                                        });
                                    } else {
                                        // notify user login observers immediately after logout
                                        notifyLoginState(lastUser);

                                        if (authenticationCallback != null)
                                            authenticationCallback.authenticationSuccess();
                                    }

                                } catch (JSONException e) {
                                    if (logoutOnAuthError) {
                                        logout();
                                    }
                                    if (authenticationCallback != null) {
                                        authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.AUTHENTICATION_ERROR);
                                    }
                                    e.printStackTrace();
                                }
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                handleRequestError(error);
                                //logout(); // User won't be signed off on connection error. User needs to keep being logged in also in case device has no internet connection
                                if (authenticationCallback != null) {
                                    UserAuthenticationCallback.AuthenticationErrorType errorType = UserAuthenticationCallback.AuthenticationErrorType.CONNECTION_ERROR;

                                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                                        errorType = UserAuthenticationCallback.AuthenticationErrorType.AUTHENTICATION_ERROR;
                                    }

                                    authenticationCallback.authenticationError(errorType);
                                }
                            }
                        }
                );

        if (customAuthToken != null) {
            authRequest.setCustomToken(customAuthToken);
        }

        testServerUrl(getServerUrl(), new RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                addRequest(authRequest);
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                if (authenticationCallback != null) {
                    authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.CONNECTION_ERROR);
                }
            }
        });


    }


    public static void authenticateUser(final String username, final String password, @NonNull final UserAuthenticationCallback authenticationCallback) {
        StringRequest jsonObjectRequest = new StringRequest
                (Request.Method.POST, getServerUrl() + Endpoints.LOGIN, new Response.Listener<String>() {

                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject responseObject = new JSONObject(response);
                            String token = responseObject.getString("access_token");
                            if (!token.isEmpty()) {
                                currentAuthToken = token;
                                fetchCurrentUserFromServer(authenticationCallback);
                            } else {
                                authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.AUTHENTICATION_ERROR);
                            }

                        } catch (JSONException e) {
                            authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.AUTHENTICATION_ERROR);
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                            authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.AUTHENTICATION_ERROR);
                        } else {
                            authenticationCallback.authenticationError(UserAuthenticationCallback.AuthenticationErrorType.CONNECTION_ERROR);
                        }

                    }
                }) {

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("username", username);
                params.put("password", password);
                return params;
            }
        };

        addRequest(jsonObjectRequest);
    }


    public static void deleteRemoteDatasetAsync(String id, RemoteDatasetType datasetType, RemoteRequestCompletedCallback responseCallback) {

        JsonObjectAuthRequest jsonObjectRequest = new JsonObjectAuthRequest
                (
                        Request.Method.DELETE,
                        getServerUrl() + datasetType.getEndpoint() + "/" + id,
                        null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                responseCallback.onResponse(response);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                handleRequestError(error);
                                int statusCode = -1;
                                if (error.networkResponse != null)
                                    statusCode = error.networkResponse.statusCode;
                                responseCallback.onError(statusCode, error.getMessage());
                            }
                        }
                );
        addRequest(jsonObjectRequest);
    }

    public static void sendRemoteDatasetAsync(JSONObject dataset, RemoteDatasetType datasetType, RemoteRequestCompletedCallback responseCallback) {
        sendRemoteDatasetAsync(dataset, datasetType, null, responseCallback);
    }

    public static void sendRemoteDatasetAsync(JSONObject dataset, RemoteDatasetType datasetType, String urlParameter, RemoteRequestCompletedCallback responseCallback) {
        if (urlParameter == null) {
            urlParameter = "";
        } else {
            urlParameter = "/" + urlParameter;
        }

        int method = dataset.has("id") ? Request.Method.PUT : Request.Method.POST; // Modify or create dataset depends on whether 'id' is going to be submitted!
        JsonObjectAuthRequest jsonObjectRequest = new JsonObjectAuthRequest
                (
                        method,
                        getServerUrl() + datasetType.getEndpoint() + urlParameter,
                        dataset,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                responseCallback.onResponse(response);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                handleRequestError(error);
                                int statusCode = -1;
                                if (error.networkResponse != null)
                                    statusCode = error.networkResponse.statusCode;
                                responseCallback.onError(statusCode, error.getMessage());
                            }
                        }
                );

        boolean increaseTimeout = false;
        if (datasetType == RemoteDatasetType.SYNC) {
            // Increase Timeout for DataSync request (esp. sending Sensor Data),
            // since these could contain larger amounts of data.
            increaseTimeout = true;
        }
        addRequest(jsonObjectRequest, increaseTimeout);
    }


    public static void getRemoteDatasetAsync(RemoteDatasetType datasetType, JSONObject requestHeader, String urlParameter, RemoteRequestCompletedCallback responseCallback) {
        if (urlParameter == null) {
            urlParameter = "";
        } else {
            urlParameter = "/" + urlParameter;
        }

        JsonObjectAuthRequest jsonObjectRequest = new JsonObjectAuthRequest
                (
                        Request.Method.GET,
                        getServerUrl() + datasetType.getEndpoint() + urlParameter,
                        null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                responseCallback.onResponse(response);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                handleRequestError(error);
                                int statusCode = -1;
                                if (error.networkResponse != null)
                                    statusCode = error.networkResponse.statusCode;
                                responseCallback.onError(statusCode, error.getMessage());
                            }
                        }
                ) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = super.getHeaders();
                if (requestHeader == null) {
                    return headers;
                }

                if (headers == null) {
                    headers = new HashMap<>();
                }

                Iterator<String> keys = requestHeader.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        String value = requestHeader.getString(key);
                        headers.put(key, value);
                    } catch (JSONException e) {
                        Log.w(LOG_TAG, "Incomplete '" + datasetType.toString() + "' request send! requestHeader must only contain String fields!");
                        e.printStackTrace();
                    }
                }

                return headers;
            }
        };
        jsonObjectRequest.setAuthorizationRequired(false);
        addRequest(jsonObjectRequest);
    }

    public static void getRemoteDatasetAsync(RemoteDatasetType datasetType, String urlParameter, RemoteRequestCompletedCallback responseCallback) {
        getRemoteDatasetAsync(datasetType, null, urlParameter, responseCallback);
    }

    public static void getRemoteDatasetAsync(RemoteDatasetType datasetType, RemoteRequestCompletedCallback responseCallback) {
        getRemoteDatasetAsync(datasetType, null, responseCallback);
    }

    public static void serverLog(int logPriority, String tag, String msg) {
        JSONObject req = new JSONObject();

        if (msg == null) {
            msg = "(null)";
        }

        String highPriorityStr = "";
        switch (logPriority) {
            case Log.WARN:
                highPriorityStr = "[WARNING] ";
                break;
            case Log.ERROR:
                highPriorityStr = "[ERROR] ";
                break;
        }

        // Implicit log to LogCat
        Log.println(logPriority, tag, msg);

        if (currentUser == null) {
            initialize(StudyCompanion.getAppContext());
        }

        msg = highPriorityStr + msg;

        try {
            req.put("msg", msg);

            // The App notified the backend about the current client id through each log message.
            // When the version code has changed compared to the one sent in the last message,
            // the server will update the value in the user's dataset in database.
            req.put("client_version", (int) BuildConfig.VERSION_CODE);
            // keep (int) cast for compatibility when upgrading to higher target SDK!

            if (currentUser == null) {
                req.put("anon_key", ANON_KEY); // add anon key to allow logging message from unauthenticated user

                JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                        (Request.Method.POST, getServerUrl() + RemoteDatasetType.LOG.getEndpoint(), req, response -> {
                        }, error -> {
                        }); // ignore response or errors

                addRequest(jsonObjectRequest);
            } else {
                BackendIO.sendRemoteDatasetAsync(req, BackendIO.RemoteDatasetType.LOG, new BackendIO.RemoteRequestCompletedCallback() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // ignore response
                    }

                    @Override
                    public void onError(int errorStatusCode, String errorMessage) {
                        // ignore errors
                    }
                });
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addRequest(Request request) {
        addRequest(request, false);
    }

    private static void addRequest(Request request, boolean increaseTimeout) {
        if (requestQueue == null) {
            initialize(StudyCompanion.getAppContext());
        }

        if (increaseTimeout) {
            // Increase Request Timeout (to reduce risk hat requests with large amount of data are interrupted)
            request.setRetryPolicy(new DefaultRetryPolicy(
                    INCREASED_REQUEST_TIMEOUT_MS,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        }

        requestQueue.add(request);

    }

    private static void handleRequestError(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
            // Computer says: "Unauthorized" - the auth token might have expired.
            Toast.makeText(StudyCompanion.getAppContext(), R.string.message_session_expired, Toast.LENGTH_LONG).show();
            logout();
        } else {
            // General connection problem (probably timeout, server not reachable)
            //Toast.makeText(StudyCompanion.getAppContext(), R.string.backend_communication_error, Toast.LENGTH_LONG).show();
            Log.w(LOG_TAG, "Connection attempt to f4f server backend failed.");
        }
    }
}
