package androidx.biometric;

import androidx.annotation.NonNull;

abstract class AuthenticationEvent {

    private AuthenticationEvent() {
    }

    static final class Error extends AuthenticationEvent {
        private final int errorCode;
        private final CharSequence errorMessage;

        Error(int errorCode, @NonNull CharSequence errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        @NonNull
        CharSequence getErrorMessage() {
            return errorMessage;
        }

        int getErrorCode() {
            return errorCode;
        }
    }

    static final class Success extends AuthenticationEvent {
        private final BiometricPrompt.AuthenticationResult result;

        Success(@NonNull BiometricPrompt.AuthenticationResult result) {
            this.result = result;
        }

        @NonNull
        BiometricPrompt.AuthenticationResult getResult() {
            return result;
        }
    }

    static final class Failure extends AuthenticationEvent {
        Failure() {
        }
    }

    //Marker of a terminal state
    static final class Complete extends AuthenticationEvent {
        Complete() {
        }
    }
}
