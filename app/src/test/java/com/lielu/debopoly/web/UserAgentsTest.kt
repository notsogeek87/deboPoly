package com.lielu.debopoly.web

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentsTest {
    @Test
    fun `retire wv, Build et Version`() {
        val webView =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.39 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.6778.39 Mobile Safari/537.36",
            UserAgents.chromeLike(webView),
        )
    }

    @Test
    fun `user-agent deja Chrome inchange`() {
        val chrome =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
        assertEquals(chrome, UserAgents.chromeLike(chrome))
    }

    @Test
    fun `version ordinateur garde la version du moteur`() {
        val webView = "Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 Version/4.0 Chrome/131.0.6778.39 Mobile"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Safari/537.36",
            UserAgents.desktop(webView),
        )
    }
}
