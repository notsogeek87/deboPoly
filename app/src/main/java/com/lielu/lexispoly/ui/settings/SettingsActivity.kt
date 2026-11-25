package com.lielu.lexispoly.ui.settings

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lielu.lexispoly.LieluApplication
import com.lielu.lexispoly.ui.theme.AppTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LieluApplication).container

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.collect {
                    if (it.secureScreen) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        // Verrouillée pendant qu'on était ici (retour après le délai) : l'écran principal redemande le code.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.appLock.locked.collect { if (it) finish() }
            }
        }

        setContent {
            AppTheme {
                val vm: SettingsViewModel =
                    viewModel(
                        factory =
                            viewModelFactory {
                                initializer {
                                    SettingsViewModel(
                                        container.settingsRepository,
                                        container.downloadStore,
                                        container.updateManager,
                                        container.updatesEnabled,
                                    )
                                }
                            },
                    )
                SettingsScreen(
                    viewModel = vm,
                    onBackClick = ::finish,
                    onWipeConfirmed = {
                        setResult(RESULT_WIPE)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        /** Renvoyé à l'écran principal, qui possède la WebView à vider. */
        const val RESULT_WIPE = RESULT_FIRST_USER + 1
    }
}
