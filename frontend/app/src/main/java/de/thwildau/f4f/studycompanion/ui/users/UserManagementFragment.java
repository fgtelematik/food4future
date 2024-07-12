package de.thwildau.f4f.studycompanion.ui.users;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.qr.QRCodeGeneratorActivity;
import de.thwildau.f4f.studycompanion.qr.QRCodeReaderActivity;
import de.thwildau.f4f.studycompanion.ui.customform.CustomFormActivity;
import de.thwildau.f4f.studycompanion.ui.customform.CustomFormFragment;

public class UserManagementFragment extends Fragment {

    private static final int PARTICIPANTS_LIST_MAX_NUMBER_LINES_AUTOCOMPLETE = 4;

    private UserManagementViewModel userManagementViewModel;

    private List<UserManagementViewModel.UserPreview> participantsList = null;
    private ArrayAdapter<UserManagementViewModel.UserPreview> participantsListArrayAdapter;
    private AutoCompleteTextView userSelectAcText;
    private View mRootView;
    private LayoutInflater mInflater;
    private ImageButton refreshButton;
    private RotateAnimation rotateAnimation = null;
    private boolean manualRefreshTriggered = false;

    private Button qrButton;
    private Button modifyButton;
    private Button sendMailButton;
    private Button deleteButton;
    private Button clearButton;


    private static final int REQUEST_CREATE_USER = 1;
    private static final int REQUEST_MODIFY_USER = 2;
    private static final int REQUEST_CHOOSE_BY_QR = 3;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        userManagementViewModel = new ViewModelProvider(this).get(UserManagementViewModel.class);

        Bundle arguments = getArguments();
        boolean manageParticipants = false;
        mInflater = inflater;

        if(arguments != null && arguments.containsKey("manageParticipants"))
        {
            manageParticipants = arguments.getBoolean("manageParticipants");
        }

        userManagementViewModel.setManageParticipants(manageParticipants);

        mRootView = inflater.inflate(manageParticipants ? R.layout.fragment_participant_management : R.layout.fragment_user_management, container, false);

        userManagementViewModel.getParticipantList().observe(getViewLifecycleOwner(), this::onUpdateUserList);
        userManagementViewModel.getUserDataToModify().observe(getViewLifecycleOwner(), this::onModifyUser);
        userManagementViewModel.getIsLoadingData().observe(getViewLifecycleOwner(), this::setLoading);
        userManagementViewModel.getRequestSwitchToUser().observe(getViewLifecycleOwner(), this::onSwitchToUser);

        userManagementViewModel.getUserAuthToken().observe(getViewLifecycleOwner(), token -> {
            if(!token.isEmpty()) {
                Intent i = new Intent(getActivity(), QRCodeGeneratorActivity.class);
                i.putExtra(QRCodeGeneratorActivity.EXTRA_QR_DATA, token);
                i.putExtra(QRCodeGeneratorActivity.EXTRA_INFOTEXT, getString(R.string.user_management_qr_infotext));
                startActivity(i);
            }
        });



        userSelectAcText = mRootView.findViewById(R.id.actUsers);
        userSelectAcText.setThreshold(0);
        userSelectAcText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(Utils.nullOrEmpty(s) ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                setModButtonsEnabled(getSelectedUser(false) != null);
            }
        });

        userSelectAcText.setOnFocusChangeListener((v, hasFocus) -> {
            if(hasFocus) {
                userSelectAcText.showDropDown();
            }
        });

        userSelectAcText.setOnClickListener(v -> userSelectAcText.showDropDown());

        clearButton = mRootView.findViewById(R.id.actUsersClearBtn);
        clearButton.setOnClickListener(this::onBtnClearClick);
        clearButton.setVisibility(View.GONE);

        refreshButton = mRootView.findViewById(R.id.btnRefresh);
        refreshButton.setOnClickListener(view -> { manualRefreshTriggered = true; userManagementViewModel.loadUsersFromServer(); });

        mRootView.findViewById(R.id.btnChooseByQr).setOnClickListener(this::onBtnChooseByQrClick);
        mRootView.findViewById(R.id.btnCreate).setOnClickListener(this::onBtnCreateClick);

        deleteButton = mRootView.findViewById(R.id.btnDelete);
        deleteButton.setOnClickListener(this::onBtnDeleteClick);

        sendMailButton = mRootView.findViewById(R.id.btnSendMail);
        sendMailButton.setOnClickListener(this::onBtnSendmailClick);

        qrButton = mRootView.findViewById(R.id.btnQR);
        qrButton.setOnClickListener(view -> {
            UserManagementViewModel.UserPreview selectedUser = getSelectedUser(true);
            if(selectedUser == null) {
                return;
            }

            userManagementViewModel.generateAuthTokenForUser(selectedUser);
        });

        modifyButton = mRootView.findViewById(R.id.btnModify);
        modifyButton.setOnClickListener(view -> {
            UserManagementViewModel.UserPreview selectedUser = getSelectedUser(true);
            if(selectedUser == null) {
                return;
            }

            userManagementViewModel.initUserUpdate(selectedUser);
        });

        setModButtonsEnabled(false);
        userManagementViewModel.loadUsersFromServer();

        return mRootView;
    }

    private void setModButtonsEnabled(boolean enabled) {
        modifyButton.setEnabled(enabled);
        qrButton.setEnabled(enabled);
        sendMailButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }



    private void setLoading(boolean loading) {
        if(rotateAnimation == null) {
            rotateAnimation = new RotateAnimation(0, 359, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(500);
            rotateAnimation.setRepeatCount(Animation.INFINITE);
            rotateAnimation.setInterpolator(new LinearInterpolator());
            refreshButton.setAnimation(rotateAnimation);
        }


        if(loading) {
            refreshButton.setRotation(0);
            rotateAnimation.reset();
            refreshButton.startAnimation(rotateAnimation);
            refreshButton.setEnabled(false);
            userSelectAcText.setEnabled(false);
        } else {
            refreshButton.getAnimation().cancel();
            refreshButton.setEnabled(true);
            userSelectAcText.setEnabled(true);
        }
    }

    private UserManagementViewModel.UserPreview getSelectedUser(boolean showToastWhenNotFound) {
        String username = userSelectAcText.getText().toString();
        UserManagementViewModel.UserPreview selectedUser = null;
        for(UserManagementViewModel.UserPreview user : participantsList) {
            if(username.equals(user.toString())) {
                selectedUser = user;
                break;
            }
        }
        if(selectedUser == null && showToastWhenNotFound) {
            Toast.makeText(requireContext(), R.string.user_invalid_username_entered, Toast.LENGTH_LONG).show();
        }

        return selectedUser;
    }

    private void onBtnSendmailClick(View view) {
        final UserManagementViewModel.UserPreview selectedUser = getSelectedUser(true);
        if(selectedUser == null) {
            return;
        }

        final View viewTextInput = LayoutInflater.from(getContext()).inflate(R.layout.dialog_text_input, (ViewGroup) getView(), false);
        final TextInputEditText textEmailAddress = viewTextInput.findViewById(R.id.textInput);
        textEmailAddress.setText("participant@example.com");

        textEmailAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        AlertDialog emailAddrDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.dialog_sendmail_title)
                .setMessage(R.string.dialog_sendmail_address_message)
                .setView(viewTextInput)
                .create();
        emailAddrDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), (DialogInterface.OnClickListener)null);
        emailAddrDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), (dialog, which) -> {

            final String emailAddress = textEmailAddress.getText().toString();

            AlertDialog pwResetDialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.dialog_sendmail_title)
                    .setMessage(R.string.dialog_sendmail_pwreset_message)
                    .create();
            pwResetDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.yes), (dialog2, which2) -> userManagementViewModel.sendRegistrationMail(selectedUser, true, emailAddress));
            pwResetDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.no), (dialog2, which2) -> userManagementViewModel.sendRegistrationMail(selectedUser, false, emailAddress));
            pwResetDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.cancel), (DialogInterface.OnClickListener)null);
            pwResetDialog.show();
        });

        emailAddrDialog.show();

    }

    private void onBtnChooseByQrClick(View v) {
        Intent i = new Intent(getActivity(), QRCodeReaderActivity.class);
        i.putExtra(QRCodeReaderActivity.EXTRA_TITLE, getString(R.string.part_management_choose_by_qr_title));
        i.putExtra(QRCodeReaderActivity.EXTRA_INFOTEXT, getString(R.string.part_management_choose_by_qr_infotext));
        startActivityForResult(i, REQUEST_CHOOSE_BY_QR);
    }


    private void onBtnDeleteClick(View v) {
        final UserManagementViewModel.UserPreview selectedUser = getSelectedUser(true);
        final User currentUser = BackendIO.getCurrentUser();
        if(selectedUser == null || currentUser == null) {
            return;
        }

        if(selectedUser.getId().equals(currentUser.id)) {
            // User tries to delete active user
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.user_delete_active_user_error)
                    .setPositiveButton(R.string.ok, null)
                    .setCancelable(true)
                    .create().show();
            return;
        }

        new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.user_delete_question, selectedUser.toString()))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> userManagementViewModel.deleteUserOnServer(selectedUser))
                .setCancelable(true)
                .create().show();
        ;
    }

    private void onBtnClearClick(View v) {
        userSelectAcText.setText("");
    }


    private void onUpdateUserList(List<UserManagementViewModel.UserPreview> users) {
//        if(participantsList == null) {

        // Re-instantiating and assigning all the adapter on every list update seems ineffective,
        // but it was the only way I could make items disappear (after user deletion),
        // which were removed from the participantsList. This might be a bug in
        // AutoCompleteTextView or something.. You can see my approaches in the commented
        // else-branch below.

            participantsList = new ArrayList<>(users);

            participantsListArrayAdapter = new ArrayAdapter<UserManagementViewModel.UserPreview>
                    (mInflater.getContext(), android.R.layout.simple_list_item_1, participantsList)
            {
                // thanks to alizeyn for this solution to fix user list goes off screen
                // https://stackoverflow.com/a/56163333/5106474

                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    TextView dropDownTextView = (TextView) super.getView(position, convertView, parent);

                    dropDownTextView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            userSelectAcText.setDropDownHeight(dropDownTextView.getHeight() * PARTICIPANTS_LIST_MAX_NUMBER_LINES_AUTOCOMPLETE);

                            dropDownTextView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                    return dropDownTextView;
                }

            };

            userSelectAcText.setAdapter(participantsListArrayAdapter);



//        } else {
//            participantsList.clear();
//            participantsList.addAll(users);
//            participantsListArrayAdapter.notifyDataSetChanged();
//                  // this won't remove deleted items from UI suggestions list
//
//            userSelectAcText.setAdapter(null);
//            new Handler(Looper.getMainLooper()).post(() -> userSelectAcText.setAdapter(participantsListArrayAdapter));
//                  // won't remove deleted items either.
//        }
    }

    private void onSwitchToUser(String userDisplayName) {
        if(manualRefreshTriggered) {
            // Don't update when user list refreshed manually
            manualRefreshTriggered = false;
            return;
        }

        if(userDisplayName == null) {
            userDisplayName = "";
        }

        if(!userSelectAcText.getText().toString().equals(userDisplayName))
            userSelectAcText.setText(userDisplayName);
    }

    private void onModifyUser(String userData) {
        if(null == userData || userData.isEmpty())
        {
            return;
        }
        Intent i = new Intent(getActivity(), CustomFormActivity.class);
        i.putExtra(CustomFormFragment.EXTRA_BUTTON_CONFIRM, userManagementViewModel.isManageParticipants() ? getString(R.string.button_save_participant) : getString(R.string.button_save_user));
        i.putExtra(CustomFormFragment.EXTRA_ADT_ID, "user");
        i.putExtra(CustomFormFragment.EXTRA_DATA_JSON, userData);
        i.putExtra(CustomFormFragment.EXTRA_TITLE, userManagementViewModel.isManageParticipants() ? getString(R.string.title_edit_participant) : getString(R.string.title_edit_user));
        startActivityForResult(i, REQUEST_MODIFY_USER);
    }


    private void onBtnCreateClick(View view) {
        Intent i = new Intent(getActivity(), CustomFormActivity.class);
        i.putExtra(CustomFormFragment.EXTRA_BUTTON_CONFIRM, userManagementViewModel.isManageParticipants() ? getString(R.string.button_create_participant) : getString(R.string.button_create_user));
        i.putExtra(CustomFormFragment.EXTRA_ADT_ID, "user");
        i.putExtra(CustomFormFragment.EXTRA_TITLE, userManagementViewModel.isManageParticipants() ? getString(R.string.title_create_participant) : getString(R.string.title_create_user));
        startActivityForResult(i, REQUEST_CREATE_USER);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(data == null) {
            return;
        }

        if(requestCode == REQUEST_CREATE_USER && resultCode == Activity.RESULT_OK) {
            try {
                String userDataStr = data.getStringExtra(CustomFormFragment.EXTRA_UPDATED_DATA_JSON);
                if(userDataStr == null)
                    return;
                JSONObject userData = new JSONObject(userDataStr);
                userManagementViewModel.upsertUserOnServer(userData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if(requestCode == REQUEST_MODIFY_USER && resultCode == Activity.RESULT_OK) {
            userManagementViewModel.updateUser(data.getStringExtra(CustomFormFragment.EXTRA_UPDATED_DATA_JSON));
        } else if(requestCode == REQUEST_CHOOSE_BY_QR && resultCode == Activity.RESULT_OK) {
            String qrContent = data.getStringExtra(QRCodeReaderActivity.EXTRA_BARCODE_DATA);
            if(qrContent == null)
                return;

            UserManagementViewModel.UserPreview chosenUser = null;

            int i = 0;
            for(UserManagementViewModel.UserPreview user : participantsList) {

                if(qrContent.equals(user.getId()) || qrContent.equals(user.getHszIdentifier())) {
                    chosenUser = user;
                    break;
                }

                i++;
            }

            if(chosenUser != null) {
                userSelectAcText.setText(chosenUser.toString());
                Toast.makeText(getContext(), getString(R.string.part_management_choose_by_qr_success, chosenUser.toString()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), getString(R.string.part_management_choose_by_qr_failure), Toast.LENGTH_LONG).show();
                userSelectAcText.setText("");
            }
        }
    }
}