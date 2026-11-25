package com.lielu.lexispoly.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 0L
    private var timeout = 5 * 60_000L
    private val lock = AppLock(timeoutMillis = { timeout }, clock = { now })

    @Test
    fun `verrouillee au lancement`() {
        assertTrue(lock.locked.value)
    }

    @Test
    fun `retour avant le delai reste deverrouille`() {
        lock.unlock()
        lock.onBackground()
        now += 4 * 60_000L
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `retour apres le delai reverrouille`() {
        lock.unlock()
        lock.onBackground()
        now += 5 * 60_000L
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `delai immediat reverrouille a chaque retour`() {
        timeout = 0
        lock.unlock()
        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `activite lancee par l app beneficie d un delai de grace`() {
        timeout = 0
        lock.unlock()
        lock.onOwnActivityLaunched()
        lock.onBackground()
        now += 60_000L
        lock.onForeground()
        assertFalse(lock.locked.value)

        // La grâce ne vaut que pour une sortie.
        lock.onBackground()
        now += 1
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `grace limitee dans le temps`() {
        timeout = 0
        lock.unlock()
        lock.onOwnActivityLaunched()
        lock.onBackground()
        now += AppLock.OWN_ACTIVITY_GRACE_MILLIS
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `premier passage au premier plan sans arriere-plan ne change rien`() {
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}
