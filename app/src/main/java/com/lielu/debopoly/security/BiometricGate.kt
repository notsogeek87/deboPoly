package com.lielu.debopoly.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lielu.debopoly.R

/** Résultat d'une demande de déverrouillage. */
sealed interface GateResult {
    data object Success : GateResult

    /** L'utilisateur a annulé ou trop d'essais : l'écran de verrouillage reste affiché. */
    data class Failed(
        val message: String,
    ) : GateResult

    /** Aucun code, schéma, mot de passe ni biométrie configuré sur l'appareil : impossible de protéger l'app. */
    data object NoDeviceSecurity : GateResult
}

/**
 * BiometricPrompt avec repli sur le code de l'appareil. Android 11+ : biométrie « forte » ou code ;
 * Android 8–10 : la combinaison forte + code n'y est pas prise en charge, on accepte donc la biométrie
 * standard ou le code.
 */
class BiometricGate(
    private val activity: FragmentActivity,
) {
    private val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    fun authenticate(onResult: (GateResult) -> Unit) {
        val status = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(GateResult.NoDeviceSecurity)
            return
        }
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onResult(GateResult.Success)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                            onResult(GateResult.NoDeviceSecurity)
                        } else {
                            onResult(GateResult.Failed(errString.toString()))
                        }
                    }
                    // onAuthenticationFailed (empreinte non reconnue) : le dialogue système reste ouvert.
                },
            )
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Déverrouiller ${activity.getString(R.string.app_name)}")
                .setSubtitle("Empreinte, visage ou code de l'appareil")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .build()
        prompt.authenticate(info)
    }
}
