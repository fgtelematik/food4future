package de.thwildau.f4f.studycompanion.ui.login;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Data validation state of the login form.
 */
public class LoginFormState {
    @Nullable
    private final Integer usernameError;
    @Nullable
    private final Integer passwordError;

    @Nullable
    private final Integer endpointError;

    private final boolean isDataValid;


    LoginFormState(@Nullable Integer endpointError, @Nullable Integer usernameError, @Nullable Integer passwordError) {
        this.usernameError = usernameError;
        this.passwordError = passwordError;
        this.endpointError = endpointError;
        this.isDataValid = false;
    }

    LoginFormState(boolean isDataValid) {
        this.usernameError = null;
        this.passwordError = null;
        this.endpointError = null;
        this.isDataValid = isDataValid;
    }

    @Nullable
    Integer getUsernameError() {
        return usernameError;
    }

    @Nullable
    Integer getPasswordError() {
        return passwordError;
    }
    Integer getEndpointError() {
        return endpointError;
    }

    boolean isDataValid() {
        return isDataValid;
    }
}