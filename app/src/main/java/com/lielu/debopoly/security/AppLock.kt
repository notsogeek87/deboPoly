package com.lielu.debopoly.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État de verrouillage de l'app, au niveau du PROCESSUS (pas d'une activité) : verrouillée au lancement, puis
 * de nouveau quand l'app revient au premier plan après plus de [timeoutMillis] en arrière-plan.
 *
 * Les sorties provoquées par l'app elle-même (sélecteur de fichiers, appareil photo, saisie du code de
 * l'appareil) ne verrouillent qu'au-delà de [OWN_ACTIVITY_GRACE_MILLIS] (ou du délai, s'il est plus long) :
 * sans cela, le délai « immédiat » redemanderait le code au retour de chaque photo jointe, et la saisie du code
 * elle-même (une autre activité sur Android 9 et moins) reverrouillerait l'app en boucle.
 */
class AppLock(
    private val timeoutMillis: () -> Long,
    private val clock: () -> Long,
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var ownActivityLaunched = false

    /** Le processus passe en arrière-plan (ProcessLifecycleOwner.onStop). */
    fun onBackground() {
        backgroundedAt = clock()
    }

    /** Le processus revient au premier plan (ProcessLifecycleOwner.onStart). */
    fun onForeground() {
        val since = backgroundedAt
        val own = ownActivityLaunched
        backgroundedAt = null
        ownActivityLaunched = false
        if (since == null) return
        val away = clock() - since
        val limit = if (own) maxOf(timeoutMillis(), OWN_ACTIVITY_GRACE_MILLIS) else timeoutMillis()
        if (away >= limit) _locked.value = true
    }

    /** À appeler juste avant de lancer une activité externe dont on attend le retour (fichiers, photo…). */
    fun onOwnActivityLaunched() {
        ownActivityLaunched = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun lock() {
        _locked.value = true
    }

    companion object {
        const val OWN_ACTIVITY_GRACE_MILLIS = 2 * 60_000L
    }
}
