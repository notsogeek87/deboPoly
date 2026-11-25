package com.lielu.lexispoly.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * WebView qui sait si le geste en cours « tire » la page vers le bas alors qu'elle est déjà tout en haut.
 *
 * Utile pour le tirer-pour-actualiser : dans une application web dont le contenu défile dans un `<div>`
 * (et non dans la page elle-même), `scrollY` reste à 0 en permanence ; se fier à lui seul relancerait le
 * chargement à chaque défilement vers le haut. Le moteur ne signale un dépassement (`onOverScrolled`) que
 * quand plus rien ne peut défiler : c'est ce signal, sur un geste vers le bas, qui autorise l'actualisation.
 */
class LexisWebView(
    context: Context,
) : WebView(context) {
    var overScrolledTop = false
        private set

    private var lastY = 0f
    private var movingDown = false

    fun resetGesture() {
        overScrolledTop = false
        movingDown = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                movingDown = event.y > lastY
                if (!movingDown) overScrolledTop = false
                lastY = event.y
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        clampedX: Boolean,
        clampedY: Boolean,
    ) {
        if (clampedY && scrollY == 0 && movingDown) overScrolledTop = true
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }
}

/** SwipeRefreshLayout qui ne s'active que sur un vrai « tirer vers le bas » en haut de la page. */
class WebSwipeRefreshLayout(
    context: Context,
) : SwipeRefreshLayout(context) {
    var webView: LexisWebView? = null
    private var evaluatingDown = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) return super.onInterceptTouchEvent(ev)
        // Au posé du doigt, SwipeRefreshLayout ne mémorise le geste que si l'enfant « ne peut pas défiler » :
        // on répond donc d'après scrollY seul, le dépassement sera évalué pendant le mouvement.
        webView?.resetGesture()
        evaluatingDown = true
        try {
            return super.onInterceptTouchEvent(ev)
        } finally {
            evaluatingDown = false
        }
    }

    override fun canChildScrollUp(): Boolean {
        val wv = webView ?: return super.canChildScrollUp()
        if (wv.scrollY > 0) return true
        if (evaluatingDown) return false
        return !wv.overScrolledTop
    }
}
