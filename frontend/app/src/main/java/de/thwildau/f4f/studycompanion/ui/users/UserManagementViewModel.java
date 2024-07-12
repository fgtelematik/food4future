package de.thwildau.f4f.studycompanion.ui.users;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;

public class UserManagementViewModel extends ViewModel {

    public static class UserPreview {
        private String id;
        private String label;
        private String hszIdentifier = null;

        public UserPreview(String id, String label, String hszIdentifier) {
            this.id = id;
            this.label = label;
            this.hszIdentifier = hszIdentifier;
        }

        public String getId() {
            return id;
        }

        public String getHszIdentifier() {
            return hszIdentifier;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    private MutableLiveData<List<UserPreview>> mParticipantList;
    private MutableLiveData<String> mUserAuthToken;
    private MutableLiveData<String> mUserDataToModify;
    private MutableLiveData<Boolean> mIsLoadingData;
    private MutableLiveData<String> mRequestSwitchToUser;
    private boolean manageParticipants = false;
    private UserPreview firstUser;

    public UserManagementViewModel() {
        mParticipantList = new MutableLiveData<>();
        mParticipantList.setValue(new ArrayList<>());

        mUserAuthToken = new MutableLiveData<>();
        mUserAuthToken.setValue("");

        mUserDataToModify = new MutableLiveData<>();
        mUserDataToModify.setValue("");

        mIsLoadingData = new MutableLiveData<>();
        mIsLoadingData.setValue(false);

        mRequestSwitchToUser = new MutableLiveData<>();
        mRequestSwitchToUser.setValue(null);
    }

    private void updateParticipantListInGUI() {
        mParticipantList.setValue(mParticipantList.getValue());
    }

    public LiveData<List<UserPreview>> getParticipantList() {
        return mParticipantList;
    }

    public MutableLiveData<String> getUserAuthToken() {
        return mUserAuthToken;
    }

    public MutableLiveData<String> getUserDataToModify() {
        return mUserDataToModify;
    }

    public MutableLiveData<Boolean> getIsLoadingData() {
        return mIsLoadingData;
    }

    public MutableLiveData<String> getRequestSwitchToUser() {
        return mRequestSwitchToUser;
    }

    public void setFirstUser(UserPreview firstUser) {
        // this user should be a dummy user, which is always the very first in List
        this.firstUser = firstUser;
    }

    public void deleteUserOnServer(UserPreview user) {
        //BackendIO.RemoteDatasetType type = manageParticipants ? BackendIO.RemoteDatasetType.PARTICIPANT : BackendIO.RemoteDatasetType.USER;
        BackendIO.RemoteDatasetType type = BackendIO.RemoteDatasetType.USER;
        BackendIO.deleteRemoteDatasetAsync(user.id, type, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                Toast.makeText(StudyCompanion.getAppContext(), StudyCompanion.getAppContext().getString(manageParticipants ? R.string.participant_deleted_message : R.string.user_deleted_message, user.label) , Toast.LENGTH_LONG).show();
                loadUsersFromServer("");
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                Toast.makeText(StudyCompanion.getAppContext(), StudyCompanion.getAppContext().getString(R.string.user_delete_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    public void upsertUserOnServer(JSONObject userData) {
        boolean newUser = !userData.has("id");

        BackendIO.RemoteDatasetType type = BackendIO.RemoteDatasetType.USER;
        BackendIO.sendRemoteDatasetAsync(userData, type, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String username = response.getString("username");

                    int messageResource = 0;
                    if(newUser) {
                        messageResource = manageParticipants ? R.string.participant_created_message : R.string.user_created_message;
                    } else {
                        messageResource = manageParticipants ? R.string.participant_updated_message : R.string.user_updated_message;
                    }

                    Toast.makeText(StudyCompanion.getAppContext(), StudyCompanion.getAppContext().getResources().getString(messageResource, username), Toast.LENGTH_LONG).show();

                    loadUsersFromServer(username);

                } catch (JSONException e) {
                    Toast.makeText(StudyCompanion.getAppContext(), R.string.invalid_data_error_message, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }

            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                int errorMsgId = R.string.participant_upsert_error_general;

                if(errorStatusCode == 409) {
                    errorMsgId = R.string.participant_upsert_error_username_exists;
                }

                Toast.makeText(StudyCompanion.getAppContext(), StudyCompanion.getAppContext().getResources().getString(errorMsgId), Toast.LENGTH_LONG).show();
            }
        });
    }
    public void loadUsersFromServer() {
        loadUsersFromServer(null);
    }

    private void loadUsersFromServer(String requestSwitchUserAfterUpdate) {
//        BackendIO.RemoteDatasetType requestType = manageParticipants ? BackendIO.RemoteDatasetType.PARTICIPANTS : BackendIO.RemoteDatasetType.USERS;
        BackendIO.RemoteDatasetType requestType = BackendIO.RemoteDatasetType.USERS;
        mIsLoadingData.setValue(true);
        BackendIO.getRemoteDatasetAsync(requestType, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                mIsLoadingData.setValue(false);
                String switchToUser = null;

                try {
                    JSONArray users = response.getJSONArray("users");

                    mParticipantList.getValue().clear();
                    if(firstUser != null) {
                        mParticipantList.getValue().add(firstUser);
                    }

                    for(int i = 0; i < users.length(); i++) {
                        JSONObject userObj = users.getJSONObject(i);

                        String name = userObj.getString("username");
                        String role = userObj.getString("role");
                        String id = userObj.getString("id");
                        String hszIdentifier = userObj.optString("hsz_identifier");
                        if(hszIdentifier.isEmpty()) hszIdentifier = null;

                        String displayName;

                        if(manageParticipants) {
                            // if hsz ID specified, show this first and the user name in brackets.
                            displayName = hszIdentifier == null ? name : hszIdentifier + " (" + name + ")";
                        } else {
                            // Show role in brackets for administrator user management
                            displayName = name + " (" + role + ")";
                        }

                        // Adjust name of user to switch to (if requested)
                        if(name.equals(requestSwitchUserAfterUpdate)) {
                            switchToUser = displayName;
                        }

                        UserPreview user = new UserPreview(id, displayName, hszIdentifier);
                        mParticipantList.getValue().add(user);
                    }
                } catch (JSONException e) {
                    Toast.makeText(StudyCompanion.getAppContext(), R.string.invalid_data_error_message, Toast.LENGTH_LONG).show();
                }

                updateParticipantListInGUI();
                mRequestSwitchToUser.setValue(switchToUser);
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                mIsLoadingData.setValue(false);
                Toast.makeText(StudyCompanion.getAppContext(), "Error fetching user data from server. Are you online?", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void generateAuthTokenForUser(UserPreview user) {
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.TOKEN, user.id, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String token = response.getString("access_token");
                    mUserAuthToken.setValue(token);
                } catch (JSONException e) {
                    e.printStackTrace();
                    mUserAuthToken.setValue("");
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                mUserAuthToken.setValue("");
            }
        });
    }

    public void initUserUpdate(UserPreview user) {
        mIsLoadingData.setValue(true);
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.USER, user.id, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                mIsLoadingData.setValue(false);
                try {
                    String userObject = response.getJSONObject("user").toString();
                    mUserDataToModify.setValue(userObject);
                } catch (JSONException e) {
                    Toast.makeText(StudyCompanion.getAppContext(), "Cannot modify user '"+user.label+"'. Invalid server response.", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                mIsLoadingData.setValue(false);
                Toast.makeText(StudyCompanion.getAppContext(), "Cannot modify user '"+user.label+"'. Server communication problem or user does not exist.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void sendRegistrationMail(UserPreview user, boolean resetPassword, @NonNull String emailAddress) {
        JSONObject request = new JSONObject();

        try {
            if(resetPassword) {
                request.put("reset_password", true);
            }
            request.put("email", emailAddress);
        } catch (JSONException e) {
            // won't happen
        }
        BackendIO.sendRemoteDatasetAsync(request, BackendIO.RemoteDatasetType.SENDMAIL, user.id, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                String msg;
                Context context = StudyCompanion.getAppContext();
                if(!response.optBoolean("success")) {
                    msg = "Registration E-Mail could NOT be sent successfully. Is a valid e-mail address configured for this user?";
                } else {
                    msg = "Registration E-Mail sent successfully" + (resetPassword ? " and reset password." : ".");
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                Toast.makeText(StudyCompanion.getAppContext(), "Error on handling request. Server communication problem or user does not exist.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void updateUser(String userDataset) {
        String username = "";
        JSONObject userObject = null;

        try {
            userObject = new JSONObject(userDataset);
            username = userObject.getString("username");
        } catch (JSONException e) {
            Toast.makeText(StudyCompanion.getAppContext(), "Unexpected error on user data modification. Please contact study administrator.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }

        String finalUsername = username;

        BackendIO.sendRemoteDatasetAsync(userObject, BackendIO.RemoteDatasetType.USER, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                Toast.makeText(StudyCompanion.getAppContext(), "User with username '"+ finalUsername +"' successfully updated.", Toast.LENGTH_LONG).show();
                loadUsersFromServer(finalUsername);
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                int errorMsgId = R.string.participant_upsert_error_general;

                if(errorStatusCode == 409) {
                    errorMsgId = R.string.participant_upsert_error_username_exists;
                }

                Toast.makeText(StudyCompanion.getAppContext(), StudyCompanion.getAppContext().getResources().getString(errorMsgId), Toast.LENGTH_LONG).show();
            }
        });
    }


    public void setManageParticipants(boolean manageParticipants) {
        this.manageParticipants = manageParticipants;
    }

    public boolean isManageParticipants() {
        return manageParticipants;
    }


}