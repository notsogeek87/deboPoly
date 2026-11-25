package com.lielu.lexispoly

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class LieluApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Premier plan / arrière-plan de l'app entière (et non d'une activité) : passer de l'écran principal aux
        // réglages ne compte pas comme une sortie de l'app.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.appLock.onForeground()

                override fun onStop(owner: LifecycleOwner) = container.appLock.onBackground()
            },
        )
        container.downloadStore.purgeStaleCameraFiles()
    }
}
