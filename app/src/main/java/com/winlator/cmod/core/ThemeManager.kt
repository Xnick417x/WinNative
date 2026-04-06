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

    private var currentTheme: Int = THEME_CLASSIC_DARK

    @JvmStatic
    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        currentTheme = prefs.getInt(PREF_APP_THEME, THEME_CLASSIC_DARK)
    }

    @JvmStatic
    fun setTheme(context: Context, themeId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt(PREF_APP_THEME, themeId).apply()
        currentTheme = themeId
    }

    @JvmStatic
    fun getCurrentTheme(): Int = currentTheme

    fun isClassicDark(): Boolean = currentTheme == THEME_CLASSIC_DARK

    // --- Compose Colors ---

    fun getComposeColorScheme(): ColorScheme {
        return if (isClassicDark()) {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFFA855F7), // Purple Accent
                background = androidx.compose.ui.graphics.Color(0xFF0F0F12), // Deep Charcoal
                surface = androidx.compose.ui.graphics.Color(0xFF1A1A26),    // Dark Dark Purple Surface
                onSurface = androidx.compose.ui.graphics.Color(0xFFF0F4FF),
                outline = androidx.compose.ui.graphics.Color(0xFF2E2E44)     // Matching Purple-ish Border
            )
        } else {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF1A9FFF), // WinNative Blue
                background = androidx.compose.ui.graphics.Color(0xFF0F1724), // Navy
                surface = androidx.compose.ui.graphics.Color(0xFF1B2A3D),    // Lighter Navy
                onSurface = androidx.compose.ui.graphics.Color(0xFFF5F9FF),
                outline = androidx.compose.ui.graphics.Color(0xFF2D425A)
            )
        }
    }

    val colorBackground: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF0F0F12) else androidx.compose.ui.graphics.Color(0xFF0F1724)

    val colorSurface: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF1A1A26) else androidx.compose.ui.graphics.Color(0xFF1B2A3D)

    val colorSurfaceVariant: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF242436) else androidx.compose.ui.graphics.Color(0xFF1C2D42)
        
    val colorSurfaceHighlight: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF2E2E44) else androidx.compose.ui.graphics.Color(0xFF2A4066)

    val colorOutline: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF2E2E44) else androidx.compose.ui.graphics.Color(0xFF2D425A)

    val colorTextPrimary: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFFF0F4FF) else androidx.compose.ui.graphics.Color(0xFFF5F9FF)

    val colorTextSecondary: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF7A8FA8) else androidx.compose.ui.graphics.Color(0xFF9CB0C7)

    val colorAccent: androidx.compose.ui.graphics.Color
        @androidx.compose.runtime.Composable
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFFA855F7) else androidx.compose.ui.graphics.Color(0xFF1A9FFF)

    val colorStatusGreen: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF3FB950)
    val colorWarningAmber: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFC857)
    val colorDangerRed: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF7A88)

    // --- Java/XML Colors ---

    @JvmStatic
    fun getBackgroundColor(): Int {
        return if (isClassicDark()) Color.parseColor("#0F0F12") else Color.parseColor("#0F1724")
    }

    @JvmStatic
    fun getSurfaceColor(): Int {
        return if (isClassicDark()) Color.parseColor("#1A1A26") else Color.parseColor("#1B2A3D")
    }

    @JvmStatic
    fun applySystemUiTheme(activity: Activity) {
        val window = activity.window ?: return
        window.statusBarColor = getBackgroundColor()
        window.navigationBarColor = getBackgroundColor()

        val decorView = window.decorView ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, // 0 for dark theme (light text)
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            decorView.systemUiVisibility = flags
        }
    }
}
