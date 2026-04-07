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
import com.winlator.cmod.R

object ThemeManager {
    const val PREF_APP_THEME = "app_theme"
    const val THEME_BLUE = 0
    const val THEME_DEFAULT = 1

    private var currentTheme: Int = THEME_DEFAULT

    @JvmStatic
    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        currentTheme = prefs.getInt(PREF_APP_THEME, THEME_DEFAULT)
    }

    @JvmStatic
    fun setTheme(context: Context, themeId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt(PREF_APP_THEME, themeId).apply()
        currentTheme = themeId
    }

    @JvmStatic
    fun getCurrentTheme(): Int = currentTheme

    @JvmStatic
    fun isClassicDark(): Boolean = currentTheme == THEME_DEFAULT

    // --- Compose Colors ---

    fun getComposeColorScheme(): ColorScheme {
        return if (isClassicDark()) {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF7C3AED), // Darker Purple
                background = androidx.compose.ui.graphics.Color(0xFF0F0F12),
                surface = androidx.compose.ui.graphics.Color(0xFF16171E),
                onSurface = androidx.compose.ui.graphics.Color(0xFFF0F4FF),
                outline = androidx.compose.ui.graphics.Color(0xFF2A2C40),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFF424245) // Surface from ShortcutFragment
            )
        } else {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF57CBDE), // SetupWizard Primary
                secondary = androidx.compose.ui.graphics.Color(0xFF3B82F6), // SetupWizard Secondary
                background = androidx.compose.ui.graphics.Color(0xFF0D1117), // SetupWizard Background
                surface = androidx.compose.ui.graphics.Color(0xFF161B22),    // SetupWizard Surface
                onSurface = androidx.compose.ui.graphics.Color(0xFFF0F4FF),
                outline = androidx.compose.ui.graphics.Color(0xFF30363D)
            )
        }
    }

    @JvmStatic
    fun getThemeId(): Int = if (isClassicDark()) R.style.AppTheme_Dark else R.style.AppTheme_Blue

    val colorBackground: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF0F0F12) else androidx.compose.ui.graphics.Color(0xFF0D1117)

    val colorSurface: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF16171E) else androidx.compose.ui.graphics.Color(0xFF161B22)

    val colorSurfaceVariant: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF424245) else androidx.compose.ui.graphics.Color(0xFF21262D)
        
    val colorSurfaceHighlight: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF242436) else androidx.compose.ui.graphics.Color(0xFF30363D)

    val colorOutline: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF2A2C40) else androidx.compose.ui.graphics.Color(0xFF30363D)

    val colorTextPrimary: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFFF0F4FF) else androidx.compose.ui.graphics.Color(0xFFF0F4FF)

    val colorTextSecondary: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF7A8FA8) else androidx.compose.ui.graphics.Color(0xFF8B949E)

    val colorAccent: androidx.compose.ui.graphics.Color
        get() = if (isClassicDark()) androidx.compose.ui.graphics.Color(0xFF7C3AED) else androidx.compose.ui.graphics.Color(0xFF57CBDE)

    val colorStatusGreen: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF3FB950)
    val colorWarningAmber: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFC857)
    val colorDangerRed: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFF7A88)

    // --- Java/XML Colors ---

    @JvmStatic
    fun getBackgroundColor(): Int {
        return if (isClassicDark()) Color.parseColor("#0F0F12") else Color.parseColor("#0D1117")
    }

    @JvmStatic
    fun getSurfaceColor(): Int {
        return if (isClassicDark()) Color.parseColor("#16171E") else Color.parseColor("#161B22")
    }

    @JvmStatic
    fun getAccentColor(): Int {
        return if (isClassicDark()) Color.parseColor("#7C3AED") else Color.parseColor("#57CBDE")
    }

    @Suppress("DEPRECATION")
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
