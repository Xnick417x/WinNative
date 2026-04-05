package com.winlator.cmod.google

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

class GoogleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF1A9FFF),
                        background = Color(0xFF0F1724),
                        surface = Color(0xFF141F30),
                    )
                ) {
                    GoogleScreen()
                }
            }
        }

        val density = resources.displayMetrics.density
        val scrollView = ScrollView(requireContext()).apply {
            isFillViewport = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            scrollBarSize = (3 * density).toInt()
            isScrollbarFadingEnabled = true
            addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        return FrameLayout(requireContext()).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0F1724"))
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = (10 * density).toInt() }
            )
        }
    }

    fun onSavedGamesPermissionResult(resultCode: Int, data: Intent?) {
        val currentActivity = activity ?: return
        CloudSyncManager.onSavedGamesPermissionResult(currentActivity)
    }
}
