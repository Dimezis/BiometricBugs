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
