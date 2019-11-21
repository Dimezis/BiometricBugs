package com.eightbitlab.biometricbugs

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //FIXME add (savedInstanceState == null) check to see a different leak
//        if (savedInstanceState == null) {
            BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        toast(errString)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        toast("Success")
                    }

                    override fun onAuthenticationFailed() {
                        toast("Failed")
                    }
                }).authenticate(promptInfo())
//        }
    }

    private fun promptInfo() = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Please fix your bugs")
        .setNegativeButtonText("No")
        .build()

    private fun toast(text: CharSequence) {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }
}
