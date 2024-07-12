package de.thwildau.f4f.studycompanion.ui.users;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.ui.customform.CustomFormFragment;

public class ParticipantProfileFragment extends Fragment {

    private View rootView;
    private CustomFormFragment formFragment = null;
    private FragmentContainerView formFragmentContainer;
    private String participantData = null;

    private static final String DATA_IS_LOADED = "DATA_IS_LOADED" ;

    ParticipantProfileViewModel participantProfileViewModel;

    public ParticipantProfileFragment() {
        // Required empty public constructor
    }

    public static ParticipantProfileFragment newInstance() {
        return new ParticipantProfileFragment();
    }

    private boolean createCustomFormFragmentForParticipantData() {
        Fragment restoredFormFragment = getChildFragmentManager().findFragmentById(R.id.fragment_container);
        if(restoredFormFragment instanceof CustomFormFragment) {
            // Fragment was already created (e.g. before Screen rotation), so use existing fragment
            formFragmentContainer.setVisibility(View.VISIBLE);
            formFragment = (CustomFormFragment) restoredFormFragment;
            formFragment.setCustomFormCallback(customFormCallback);
            return true;
        }

        if(participantData == null) {
            return false;
        }

        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        if(formFragment != null) {
            fragmentTransaction.remove(formFragment);
        }

        formFragmentContainer.setVisibility(View.VISIBLE);

        Bundle formParams = new Bundle();
        formParams.putString(CustomFormFragment.EXTRA_ADT_ID, "anamnesis_data");
        formParams.putString(CustomFormFragment.EXTRA_BUTTON_CONFIRM, getString(R.string.button_save));
        formParams.putString(CustomFormFragment.EXTRA_DATA_JSON, participantData);

        formFragment = CustomFormFragment.newInstance(formParams, customFormCallback);

        fragmentTransaction
                .add(R.id.fragment_container, formFragment)
                .commit();

        return true;
    }


    private void setError(Integer messageId) {
        View errorView = rootView.findViewById(R.id.errorView);
        TextView errorText = rootView.findViewById(R.id.errorText);

        errorView.setVisibility(messageId == null ? View.GONE : View.VISIBLE);
        formFragmentContainer.setVisibility(messageId != null ? View.GONE : View.VISIBLE);
        if(messageId != null) {
            errorText.setText(messageId);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        participantProfileViewModel = new ViewModelProvider(this).get(ParticipantProfileViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_participant_profile, container, false);

        formFragmentContainer = rootView.findViewById(R.id.fragment_container);
        formFragmentContainer.setVisibility(View.GONE);

        LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        participantProfileViewModel.getErrorOnLoad().observe(viewLifecycleOwner, error -> handleError(false, error));
        participantProfileViewModel.getErrorOnSave().observe(viewLifecycleOwner, error -> handleError(true, error));
        participantProfileViewModel.getIsLoadingData().observe(viewLifecycleOwner, this::setLoading );
        participantProfileViewModel.getParticipantData().observe(viewLifecycleOwner, pParticipantData -> { participantData = pParticipantData; createCustomFormFragmentForParticipantData(); });
        participantProfileViewModel.getSaveSuccessful().observe(viewLifecycleOwner, this::handleSaveSuccess);

        if(savedInstanceState != null && savedInstanceState.getBoolean(DATA_IS_LOADED)) {
            createCustomFormFragmentForParticipantData(); // Restore Fragment from former state
        } else {
            participantProfileViewModel.loadParticipantDataAsync();
        }

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if(participantData != null) {
            outState.putBoolean(DATA_IS_LOADED, true);
        }
    }

    private void setLoading(boolean loading) {
        View loadingView = rootView.findViewById(R.id.loadingView);
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        formFragmentContainer.setVisibility(!loading ? View.VISIBLE : View.GONE);
    }

    private void handleError(boolean isSaveError, ParticipantProfileViewModel.Error error) {
        if(error == null) {
            setError(null);
        } else {
            int errorMessageID = 0;
            switch(error) {
                case InvalidDataError:
                    errorMessageID = R.string.participant_profile_error_invalid_data;
                    break;
                case AuthorizationError:
                    errorMessageID = R.string.participant_profile_error_authorization;
                    break;
                case NetworkError:
                    errorMessageID = R.string.participant_profile_error_network;
                    break;
            }
            setError(errorMessageID);
            if(!isSaveError) {
                formFragmentContainer.setVisibility(View.GONE); // hide participant form on error while loading data
                participantData = null;
            } else {
                Toast.makeText(getContext(), R.string.participant_profile_error_toast_message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private final Utils.FragmentWrapperActivityCallback customFormCallback =  new Utils.FragmentWrapperActivityCallback() {
        @Override
        public void onCustomFormResult(boolean cancelled, @Nullable String updatedDataJson) {
            if(cancelled) {
                // Navigate back to Status screen if changes are discarded.

                FragmentActivity activity = getActivity();
                if(activity != null) {
                    NavController navController = Navigation.findNavController(activity, R.id.nav_host_fragment);
                    navController.popBackStack();
                }
                return;
            }

            participantProfileViewModel.saveParticipantDataAsync(updatedDataJson);
        }
    };

    private void handleSaveSuccess(boolean success) {
        if(success) {
            Toast.makeText(getContext(), R.string.participant_profile_save_success_toast_message, Toast.LENGTH_LONG).show();
        }
    }

}