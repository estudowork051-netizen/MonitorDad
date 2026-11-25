package com.dohguard.shared.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Utilitário centralizado para aplicar modo Immersive Full Screen compatível com
 * Android 8.0+ (API 26+) até Android 14/15+, preservando gestos de borda do sistema.
 */
object ImmersiveModeHelper {

    fun applyImmersiveMode(activity: Activity) {
        val window = activity.window ?: return

        // Edge-to-edge sem reservar espaço fixo para barras do sistema
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Aproveita área de recorte (display cutout / notch) quando disponível (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
