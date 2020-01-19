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
