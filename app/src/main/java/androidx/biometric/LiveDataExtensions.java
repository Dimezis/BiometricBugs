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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

class LiveDataExtensions {
    static LiveData<AuthenticationEvent> toConsumableEvents(MutableLiveData<AuthenticationEvent> authenticationEvents) {
        MediatorLiveData<AuthenticationEvent> oneShotEventMediator = new MediatorLiveData<>();

        //MediatorLiveData makes sure that if onChanged is called, there's a consumer that
        //received the event. In this case, we clear the LiveData
        //cache so we don't repeat it next time the observer subscribes.
        oneShotEventMediator.addSource(authenticationEvents, new Observer<AuthenticationEvent>() {
            @Override
            public void onChanged(AuthenticationEvent authenticationEvent) {
                oneShotEventMediator.setValue(authenticationEvent);
                if (!(authenticationEvent instanceof AuthenticationEvent.Consumed)) {
                    authenticationEvents.setValue(new AuthenticationEvent.Consumed());
                }
            }
        });
        return oneShotEventMediator;
    }
}
