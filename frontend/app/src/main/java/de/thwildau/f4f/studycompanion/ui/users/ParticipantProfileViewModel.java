package de.thwildau.f4f.studycompanion.ui.users;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;

public class ParticipantProfileViewModel extends ViewModel {

    public enum Error {
        AuthorizationError,
        NetworkError,
        InvalidDataError
    }

    private final MutableLiveData<Error> mErrorOnLoad;
    private final MutableLiveData<Error> mErrorOnSave;
    private final MutableLiveData<String> mParticipantData;
    private final MutableLiveData<Boolean> mIsLoadingData;
    private final MutableLiveData<Boolean> mSaveSuccessful;


    public ParticipantProfileViewModel() {
        mErrorOnLoad = new MutableLiveData<>(null);
        mErrorOnSave = new MutableLiveData<>(null);
        mParticipantData = new MutableLiveData<>(null);
        mIsLoadingData = new MutableLiveData<>(false);
        mSaveSuccessful = new MutableLiveData<>(false);
    }

    public void loadParticipantDataAsync() {
        User participant = BackendIO.getCurrentUser();

        if (participant == null || participant.role != Role.Participant) {
            mErrorOnLoad.postValue(Error.AuthorizationError);
            return;
        }

        mErrorOnLoad.postValue(null);
        mIsLoadingData.postValue(true);

        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.USER, participant.id, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                mIsLoadingData.postValue(false);
                JSONObject userDataObject = response.optJSONObject("user");
                if (userDataObject == null) {
                    mErrorOnLoad.postValue(Error.InvalidDataError);
                    return;
                }
                JSONObject participantDataObject = userDataObject.optJSONObject("anamnesis_data");
                if (participantDataObject == null) {
                    mParticipantData.postValue("{}"); // empty object if not yet existing on server
                } else {
                    mParticipantData.postValue(participantDataObject.toString());
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                mErrorOnLoad.postValue(Error.NetworkError);
                mIsLoadingData.postValue(false);
            }
        });
    }

    public void saveParticipantDataAsync(String participantDataString) {
        User participant = BackendIO.getCurrentUser();

        if (participant == null || participant.role != Role.Participant) {
            mErrorOnSave.postValue(Error.AuthorizationError);
            return;
        }

        mErrorOnSave.postValue(null);
        mIsLoadingData.postValue(true);
        mSaveSuccessful.postValue(false);

        // Pepare user JSON object only containing user ID and updated anamnesis_data
        JSONObject newUserData = new JSONObject();
        try {
            newUserData.put("id", participant.id);
            newUserData.put("anamnesis_data", new JSONObject(participantDataString));
        } catch (JSONException e) {
            // JSON String could not be parsed. Break...
            mIsLoadingData.postValue(false);
            mErrorOnSave.postValue(Error.InvalidDataError);
            e.printStackTrace();
            return;
        }

        // send updated data to server
        BackendIO.sendRemoteDatasetAsync(newUserData, BackendIO.RemoteDatasetType.USER, new BackendIO.RemoteRequestCompletedCallback() {
                    @Override
                    public void onResponse(JSONObject response) {
                        mIsLoadingData.postValue(false);
                        if(response.optBoolean("success")) {
                            mSaveSuccessful.postValue(true);
                        } else {
                            mErrorOnSave.postValue(Error.InvalidDataError);
                        }
                    }

                    @Override
                    public void onError(int errorStatusCode, String errorMessage) {
                        mErrorOnSave.postValue(Error.NetworkError);
                        mIsLoadingData.postValue(false);
                    }
                }
        );
    }

    public MutableLiveData<Error> getErrorOnLoad() {
        return mErrorOnLoad;
    }

    public MutableLiveData<Error> getErrorOnSave() {
        return mErrorOnSave;
    }

    public MutableLiveData<String> getParticipantData() {
        return mParticipantData;
    }

    public MutableLiveData<Boolean> getIsLoadingData() {
        return mIsLoadingData;
    }

    public MutableLiveData<Boolean> getSaveSuccessful() {
        return mSaveSuccessful;
    }
}
