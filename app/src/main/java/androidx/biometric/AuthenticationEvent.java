/*
        Copyright 2020 Dmytro Saviuk

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
*/

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

    static final class Cancel extends AuthenticationEvent {
        Cancel() {
        }
    }

    //Marker of a consumed event
    static final class Consumed extends AuthenticationEvent {
        Consumed() {
        }
    }
}
