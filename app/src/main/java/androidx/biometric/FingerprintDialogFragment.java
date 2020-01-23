/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.biometric;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.eightbitlab.biometricbugs.R;

/**
 * This class implements a custom AlertDialog that prompts the user for fingerprint authentication.
 * This class is not meant to be preserved across process death; for security reasons, the
 * BiometricPromptCompat will automatically dismiss the dialog when the activity is no longer in the
 * foreground.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint("SyntheticAccessor")
public class FingerprintDialogFragment extends DialogFragment {

    private static final String TAG = "FingerprintDialogFrag";
    private static final String KEY_DIALOG_BUNDLE = "SavedBundle";

    /**
     * Error/help message will show for this amount of time, unless
     * {@link Utils#shouldAlwaysHideFingerprintDialogInstantly(String)} is true.
     *
     * <p>For error messages, the dialog will also be dismissed after this amount of time. Error
     * messages will be propagated back to the application via AuthenticationCallback
     * after this amount of time.
     */
    private static final int MESSAGE_DISPLAY_TIME_MS = 2000;

    // Shows a temporary message in the help area
    static final int MSG_SHOW_HELP = 1;
    // Show an error in the help area, and dismiss the dialog afterwards
    static final int MSG_SHOW_ERROR = 2;
    // Dismisses the authentication dialog
    static final int MSG_DISMISS_DIALOG_ERROR = 3;
    // Resets the help message
    static final int MSG_RESET_MESSAGE = 4;
    // Dismisses the authentication dialog after success.
    static final int MSG_DISMISS_DIALOG_AUTHENTICATED = 5;
    // The amount of time required that this fragment be displayed for in order that
    // we show an error message on top of the UI.
    static final int DISPLAYED_FOR_500_MS = 6;

    // States for icon animation
    private static final int STATE_NONE = 0;
    private static final int STATE_FINGERPRINT = 1;
    private static final int STATE_FINGERPRINT_ERROR = 2;
    private static final int STATE_FINGERPRINT_AUTHENTICATED = 3;

    /**
     * Creates a dialog requesting for Fingerprint authentication.
     */
    static FingerprintDialogFragment newInstance() {
        FingerprintDialogFragment fragment = new FingerprintDialogFragment();
        return fragment;
    }

    static final class H extends Handler {

        @Nullable
        private FingerprintDialogFragment receiver;

        H() {
        }

        void onResume(@NonNull FingerprintDialogFragment fragment) {
            this.receiver = fragment;
        }

        void onPause() {
            this.receiver = null;
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            if (receiver == null || receiver.getContext() == null) {
                Log.e(TAG, "Received a message when was detached: " + msg.what);
                return;
            }
            Context context = receiver.getContext();

            switch (msg.what) {
                case MSG_SHOW_HELP:
                    receiver.handleShowHelp((CharSequence) msg.obj, context);
                    break;
                case MSG_SHOW_ERROR:
                    receiver.handleShowError((CharSequence) msg.obj, context);
                    break;
                case MSG_DISMISS_DIALOG_ERROR:
                    receiver.handleDismissDialogError((CharSequence) msg.obj, context);
                    break;
                case MSG_DISMISS_DIALOG_AUTHENTICATED:
                    receiver.dismissSafely();
                    break;
                case MSG_RESET_MESSAGE:
                    receiver.handleResetMessage(context);
                    break;
                case DISPLAYED_FOR_500_MS:
                    receiver.handleDismissWithDelay(context);
                    break;
            }
        }
    }

    private H mHandler = new H();
    private Bundle mBundle;
    private int mErrorColor;
    private int mTextColor;
    private int mLastState;
    private ImageView mFingerprintIcon;
    private TextView mErrorText;

    /**
     * This flag is used to control the instant dismissal of the dialog fragment. In the case where
     * the user is already locked out this dialog will not appear. In the case where the user is
     * being locked out for the first time an error message will be displayed on the UI before
     * dismissing.
     */
    private boolean mDismissInstantly = true;

    // This should be re-set by the BiometricPromptCompat each time the lifecycle changes.
    @VisibleForTesting
    DialogInterface.OnClickListener mNegativeButtonListener;

    // Also created once and retained.
    private final DialogInterface.OnClickListener mDeviceCredentialButtonListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_NEGATIVE) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            Log.e(TAG, "Failed to check device credential."
                                    + " Not supported prior to L.");
                            return;
                        }

                        Utils.launchDeviceCredentialConfirmation(
                                TAG, FingerprintDialogFragment.this.getActivity(), mBundle,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        // Dismiss the fingerprint dialog without forwarding errors.
                                        FingerprintDialogFragment.this.onCancel(dialog);
                                    }
                                });
                    }
                }
            };

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null && mBundle == null) {
            mBundle = savedInstanceState.getBundle(KEY_DIALOG_BUNDLE);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(mBundle.getCharSequence(BiometricPrompt.KEY_TITLE));

        // We have to use builder.getContext() instead of the usual getContext() in order to get
        // the appropriately themed context for this dialog.
        final View layout = LayoutInflater.from(builder.getContext())
                .inflate(R.layout.fingerprint_dialog_layout, null);

        final TextView subtitleView = layout.findViewById(R.id.fingerprint_subtitle);
        final TextView descriptionView = layout.findViewById(R.id.fingerprint_description);

        final CharSequence subtitle = mBundle.getCharSequence(
                BiometricPrompt.KEY_SUBTITLE);
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
        }

        final CharSequence description = mBundle.getCharSequence(
                BiometricPrompt.KEY_DESCRIPTION);
        if (TextUtils.isEmpty(description)) {
            descriptionView.setVisibility(View.GONE);
        } else {
            descriptionView.setVisibility(View.VISIBLE);
            descriptionView.setText(description);
        }

        mFingerprintIcon = layout.findViewById(R.id.fingerprint_icon);
        mErrorText = layout.findViewById(R.id.fingerprint_error);

        final CharSequence negativeButtonText =
                isDeviceCredentialAllowed()
                        ? getString(R.string.confirm_device_credential_password)
                        : mBundle.getCharSequence(BiometricPrompt.KEY_NEGATIVE_TEXT);
        builder.setNegativeButton(negativeButtonText, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (FingerprintDialogFragment.this.isDeviceCredentialAllowed()) {
                    mDeviceCredentialButtonListener.onClick(dialog, which);
                } else if (mNegativeButtonListener != null) {
                    mNegativeButtonListener.onClick(dialog, which);
                } else {
                    Log.w(TAG, "No suitable negative button listener.");
                }
            }
        });

        builder.setView(layout);
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(KEY_DIALOG_BUNDLE, mBundle);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mErrorColor = getThemedColorFor(android.R.attr.colorError, requireContext());
        } else {
            mErrorColor = ContextCompat.getColor(requireContext(), R.color.biometric_error_color);
        }
        mTextColor = getThemedColorFor(android.R.attr.textColorSecondary, requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.onResume(this);
        mLastState = STATE_NONE;
        updateFingerprintIcon(STATE_FINGERPRINT, requireContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        // Remove everything since the fragment is going away.
        mHandler.removeCallbacksAndMessages(null);
        mHandler.onPause();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        final FingerprintHelperFragment fingerprintHelperFragment = (FingerprintHelperFragment)
                getFragmentManager()
                        .findFragmentByTag(BiometricPrompt.FINGERPRINT_HELPER_FRAGMENT_TAG);
        if (fingerprintHelperFragment != null) {
            fingerprintHelperFragment.cancel(FingerprintHelperFragment.USER_CANCELED_FROM_USER);
        }
    }

    public void setBundle(@NonNull Bundle bundle) {
        mBundle = bundle;
    }

    private int getThemedColorFor(int attr, @NonNull Context context) {
        TypedValue tv = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attr, tv, true /* resolveRefs */);
        TypedArray arr = getActivity().obtainStyledAttributes(tv.data, new int[] {attr});

        final int color = arr.getColor(0 /* index */, 0 /* defValue */);
        arr.recycle();
        return color;
    }

    /**
     * The negative button text is persisted in the fragment, not in BiometricPromptCompat. Since
     * the dialog persists through rotation, this allows us to return this as the error text for
     * ERROR_NEGATIVE_BUTTON.
     */
    @Nullable
    protected CharSequence getNegativeButtonText() {
        return mBundle.getCharSequence(BiometricPrompt.KEY_NEGATIVE_TEXT);
    }

    void setNegativeButtonListener(DialogInterface.OnClickListener listener) {
        mNegativeButtonListener = listener;
    }

    /**
     * @return The handler; the handler is used by FingerprintHelperFragment to notify the UI of
     * changes from Fingerprint callbacks.
     */
    Handler getHandler() {
        return mHandler;
    }

    /** Attempts to dismiss this fragment while avoiding potential crashes. */
    //FIXME the error is reported sometimes
    void dismissSafely() {
        if (getFragmentManager() == null) {
            Log.e(TAG, "Failed to dismiss fingerprint dialog fragment. Fragment manager was null.");
            return;
        }
        dismissAllowingStateLoss();
    }

    /**
     * @return The effective millisecond delay to wait before hiding the dialog, while respecting
     * the result of {@link Utils#shouldAlwaysHideFingerprintDialogInstantly(String)}.
     */
    static int getHideDialogDelay(Context context) {
        return context != null && Utils.shouldHideFingerprintDialog(
                context, Build.MODEL) ? 0 : MESSAGE_DISPLAY_TIME_MS;
    }

    private boolean isDeviceCredentialAllowed() {
        return mBundle.getBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL);
    }

    private boolean shouldAnimateForTransition(int oldState, int newState) {
        if (oldState == STATE_NONE && newState == STATE_FINGERPRINT) {
            return false;
        } else if (oldState == STATE_FINGERPRINT && newState == STATE_FINGERPRINT_ERROR) {
            return true;
        } else if (oldState == STATE_FINGERPRINT_ERROR && newState == STATE_FINGERPRINT) {
            return true;
        } else if (oldState == STATE_FINGERPRINT && newState == STATE_FINGERPRINT_AUTHENTICATED) {
            // TODO(b/77328470): add animation when fingerprint is authenticated
            return false;
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private Drawable getAnimationForTransition(int oldState, int newState, @NonNull Context context) {
        int iconRes;

        if (oldState == STATE_NONE && newState == STATE_FINGERPRINT) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_FINGERPRINT && newState == STATE_FINGERPRINT_ERROR) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_FINGERPRINT_ERROR && newState == STATE_FINGERPRINT) {
            iconRes = R.drawable.fingerprint_dialog_error_to_fp;
        } else if (oldState == STATE_FINGERPRINT
                && newState == STATE_FINGERPRINT_AUTHENTICATED) {
            // TODO(b/77328470): add animation when fingerprint is authenticated
            iconRes = R.drawable.fingerprint_dialog_error_to_fp;
        } else {
            return null;
        }
        return context.getDrawable(iconRes);
    }

    private void updateFingerprintIcon(int newState, @NonNull Context context) {
        // May be null if we're intentionally suppressing the dialog.
        if (mFingerprintIcon == null) {
            return;
        }

        // Devices older than this do not have FP support (and also do not support SVG), so it's
        // fine for this to be a no-op. An error is returned immediately and the dialog is not
        // shown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Drawable icon = getAnimationForTransition(mLastState, newState, context);
            if (icon == null) {
                return;
            }

            final AnimatedVectorDrawable animation = icon instanceof AnimatedVectorDrawable
                    ? (AnimatedVectorDrawable) icon
                    : null;

            mFingerprintIcon.setImageDrawable(icon);
            if (animation != null && shouldAnimateForTransition(mLastState, newState)) {
                animation.start();
            }

            mLastState = newState;
        }
    }

    private void handleShowHelp(CharSequence msg, @NonNull Context context) {
        updateFingerprintIcon(STATE_FINGERPRINT_ERROR, context);
        mHandler.removeMessages(MSG_RESET_MESSAGE);

        // May be null if we're intentionally suppressing the dialog.
        if (mErrorText != null) {
            mErrorText.setTextColor(mErrorColor);
            mErrorText.setText(msg);
        }

        // Reset the text after a delay
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_RESET_MESSAGE),
                MESSAGE_DISPLAY_TIME_MS);
    }

    private void handleShowError(CharSequence msg, @NonNull Context context) {
        updateFingerprintIcon(STATE_FINGERPRINT_ERROR, context);
        mHandler.removeMessages(MSG_RESET_MESSAGE);

        // May be null if we're intentionally suppressing the dialog.
        if (mErrorText != null) {
            mErrorText.setTextColor(mErrorColor);
            mErrorText.setText(msg);
        }

        // Dismiss the dialog after a delay
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_DISMISS_DIALOG_ERROR),
                getHideDialogDelay(context));
    }

    private void dismissAfterDelay(CharSequence msg, @NonNull Context context) {
        // May be null if we're intentionally suppressing the dialog.
        if (mErrorText != null) {
            mErrorText.setTextColor(mErrorColor);
            if (msg != null) {
                mErrorText.setText(msg);
            } else {
                mErrorText.setText(R.string.fingerprint_error_lockout);
            }
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                FingerprintDialogFragment.this.dismissSafely();
            }
        }, getHideDialogDelay(context));
    }

    private void handleDismissDialogError(CharSequence msg, @NonNull Context context) {
        if (mDismissInstantly) {
            dismissSafely();
        } else {
            dismissAfterDelay(msg, context);
        }
        // Always set this to true. In case the user tries to authenticate again the UI will not be
        // shown.
        mDismissInstantly = true;
    }

    private void handleResetMessage(@NonNull Context context) {
        updateFingerprintIcon(STATE_FINGERPRINT, context);

        // May be null if we're intentionally suppressing the dialog.
        if (mErrorText != null) {
            mErrorText.setTextColor(mTextColor);
            mErrorText.setText(context.getString(R.string.fingerprint_dialog_touch_sensor));
        }
    }

    private void handleDismissWithDelay(@NonNull Context context) {
        mDismissInstantly = Utils.shouldHideFingerprintDialog(context, Build.MODEL);
    }
}
