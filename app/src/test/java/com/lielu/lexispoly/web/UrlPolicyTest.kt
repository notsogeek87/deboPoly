package com.lielu.lexispoly.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private val policy = UrlPolicy(listOf("lexisnexis.fr", ".LexisNexis.com "))

    @Test
    fun `domaine et sous-domaines autorises, suffixe trompeur refuse`() {
        assertTrue(policy.isAllowedHost("lexisnexis.fr"))
        assertTrue(policy.isAllowedHost("poly.lexisnexis.fr"))
        assertTrue(policy.isAllowedHost("A.B.LEXISNEXIS.COM"))
        assertTrue(policy.isAllowedHost("poly.lexisnexis.fr."))
        assertFalse(policy.isAllowedHost("evil-lexisnexis.fr"))
        assertFalse(policy.isAllowedHost("lexisnexis.fr.evil.com"))
        assertFalse(policy.isAllowedHost(""))
        assertFalse(policy.isAllowedHost(null))
    }

    @Test
    fun `https autorise reste dans l app`() {
        assertEquals(UrlDecision.InApp, policy.decide("https://poly.lexisnexis.fr/dossiers?id=1"))
    }

    @Test
    fun `http autorise est force en https`() {
        assertEquals(
            UrlDecision.UpgradeToHttps("https://poly.lexisnexis.fr/a"),
            policy.decide("http://poly.lexisnexis.fr/a"),
        )
    }

    @Test
    fun `autre domaine part dans le navigateur`() {
        assertEquals(UrlDecision.ExternalBrowser, policy.decide("https://www.legifrance.gouv.fr/"))
        assertEquals(UrlDecision.ExternalBrowser, policy.decide("http://example.com/"))
    }

    @Test
    fun `identifiants dans l url refuses`() {
        assertEquals(UrlDecision.Blocked, policy.decide("https://lexisnexis.fr@evil.com/"))
        assertEquals(UrlDecision.Blocked, policy.decide("https://user:pw@poly.lexisnexis.fr/"))
    }

    @Test
    fun `schemas speciaux`() {
        assertEquals(UrlDecision.ExternalApp, policy.decide("mailto:contact@cabinet.fr"))
        assertEquals(UrlDecision.ExternalApp, policy.decide("tel:+33100000000"))
        assertEquals(UrlDecision.AndroidIntent, policy.decide("intent://x#Intent;scheme=y;end"))
        assertEquals(UrlDecision.LocalContent, policy.decide("blob:https://poly.lexisnexis.fr/uuid"))
        assertEquals(UrlDecision.LocalContent, policy.decide("data:text/plain,abc"))
        assertEquals(UrlDecision.Blocked, policy.decide("javascript:alert(1)"))
        assertEquals(UrlDecision.Blocked, policy.decide("file:///sdcard/x"))
        assertEquals(UrlDecision.Blocked, policy.decide("content://x/y"))
        assertEquals(UrlDecision.Blocked, policy.decide("pas une url"))
    }

    @Test
    fun `regles d origine pour les scripts`() {
        assertEquals(
            setOf(
                "https://lexisnexis.fr",
                "https://*.lexisnexis.fr",
                "https://lexisnexis.com",
                "https://*.lexisnexis.com",
            ),
            policy.originRules(),
        )
    }

    @Test
    fun `configuration livree coherente`() {
        val config = UrlPolicy(com.lielu.lexispoly.config.AppConfig.inAppDomains)
        assertTrue(config.isInApp(com.lielu.lexispoly.config.AppConfig.BASE_URL))
        assertTrue(config.isInApp("https://authcore.lexispoly.fr/login?ReturnUrl=%2Fconnect"))
    }

    @Test
    fun `url placeholder non https n est pas dans l app`() {
        assertFalse(policy.isInApp("<URL de connexion Lexis Poly à renseigner>"))
    }
}
