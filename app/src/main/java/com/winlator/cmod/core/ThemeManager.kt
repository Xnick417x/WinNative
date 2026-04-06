package com.winlator.cmod.core

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.preference.PreferenceManager

object ThemeManager {
    const val PREF_APP_THEME = "app_theme"
    const val THEME_WINNATIVE_BLUE = 0
    const val THEME_CLASSIC_DARK = 1

    private var currentTheme: Int = THEME_WINNATIVE_BLUE

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        currentTheme = prefs.getInt(PREF_APP_THEME, THEME_WINNATIVE_BLUE)
    }

    fun setTheme(context: Context, themeId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt(PREF_APP_THEME, themeId).apply()
        currentTheme = themeId
    }

    fun getCurrentTheme(): Int = currentTheme

    fun isClassicDark(): Boolean = currentTheme == THEME_CLASSIC_DARK

    // --- Compose Colors ---

    fun getComposeColorScheme(): ColorScheme {
        return if (isClassicDark()) {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF1A9FFF),
                background = androidx.compose.ui.graphics.Color(0xFF000000),
                surface = androidx.compose.ui.graphics.Color(0xFF14141E),
                onSurface = androidx.compose.ui.graphics.Color(0xFFF0F4FF),
            )
        } else {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF1A9FFF),
                background = androidx.compose.ui.graphics.Color(0xFF0F1724),
                surface = androidx.compose.ui.graphics.Color(0xFF1B2A3D),
                onSurface = androidx.compose.ui.graphics.Color(0xFFF5F9FF),
            )
        }
    }

    val colorBackground: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF000000) else androidx.compose.ui.graphics.Color(0xFF0F1724)

    val colorSurface: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF14141E) else androidx.compose.ui.graphics.Color(0xFF1B2A3D)

    val colorSurfaceVariant: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF1C1C28) else androidx.compose.ui.graphics.Color(0xFF1C2D42)
        
    val colorSurfaceHighlight: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF28283B) else androidx.compose.ui.graphics.Color(0xFF2A4066)

    val colorOutline: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF21212E) else androidx.compose.ui.graphics.Color(0xFF2D425A)

    val colorTextPrimary: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFFF0F4FF) else androidx.compose.ui.graphics.Color(0xFFF5F9FF)

    val colorTextSecondary: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF7A8FA8) else androidx.compose.ui.graphics.Color(0xFF9CB0C7)

    val colorStatusGreen: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF3FB950)
    val colorWarningAmber: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFC857)
    val colorDangerRed: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF7A88)

    // --- Java/XML Colors ---

    fun getBackgroundColor(): Int {
        return if (isClassicDark()) Color.parseColor("#000000") else Color.parseColor("#0F1724")
    }

    fun getSurfaceColor(): Int {
        return if (isClassicDark()) Color.parseColor("#14141E") else Color.parseColor("#1B2A3D")
    }

    fun applySystemUiTheme(activity: Activity) {
        val window = activity.window
        window.statusBarColor = getBackgroundColor()
        window.navigationBarColor = getBackgroundColor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, // 0 for dark theme (light text)
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            window.decorView.systemUiVisibility = flags
        }
    }
}
