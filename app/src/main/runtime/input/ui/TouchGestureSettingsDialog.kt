package com.winlator.cmod.runtime.input.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.*

@Composable
fun TouchGestureSettingsDialog(
    config: TouchGestureConfig,
    onConfigChange: (TouchGestureConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val dialogWidth = (configuration.screenWidthDp.dp * 0.9f).coerceAtMost(400.dp)
    val dialogHeight = (configuration.screenHeightDp.dp * 0.8f).coerceAtMost(600.dp)

    var currentConfig by remember { mutableStateOf(config.copy()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = dialogHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(WinNativeSurface)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(16.dp))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WinNativePanel)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.touch_gestures),
                    color = WinNativeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Two Finger Pan
                GestureSettingRow(
                    label = stringResource(R.string.touch_gestures_two_finger_pan),
                    selectedOption = currentConfig.twoFingerPanAction.name,
                    options = TouchGestureConfig.PanAction.entries.map { it.name to getPanActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(twoFingerPanAction = TouchGestureConfig.PanAction.valueOf(it)) }
                )

                // Pinch
                GestureToggleSettingRow(
                    label = stringResource(R.string.touch_gestures_pinch),
                    checked = currentConfig.pinchEnabled,
                    onCheckedChange = { currentConfig = currentConfig.copy(pinchEnabled = it) },
                    selectedOption = currentConfig.pinchAction.name,
                    options = TouchGestureConfig.PinchAction.entries.map { it.name to getPinchActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(pinchAction = TouchGestureConfig.PinchAction.valueOf(it)) }
                )

                // Three Finger Tap
                GestureSettingRow(
                    label = stringResource(R.string.touch_gestures_three_finger_tap),
                    selectedOption = currentConfig.threeFingerTapAction.name,
                    options = TouchGestureConfig.TapAction.entries.map { it.name to getTapActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(threeFingerTapAction = TouchGestureConfig.TapAction.valueOf(it)) }
                )

                // Four Finger Tap
                GestureSettingRow(
                    label = stringResource(R.string.touch_gestures_four_finger_tap),
                    selectedOption = currentConfig.fourFingerTapAction.name,
                    options = TouchGestureConfig.TapAction.entries.map { it.name to getTapActionLabel(it) },
                    onOptionSelected = { currentConfig = currentConfig.copy(fourFingerTapAction = TouchGestureConfig.TapAction.valueOf(it)) }
                )
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f))
                ) {
                    Text(stringResource(R.string.common_ui_cancel), color = WinNativeTextPrimary)
                }
                Button(
                    onClick = {
                        onConfigChange(currentConfig)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WinNativeAccent)
                ) {
                    Text(stringResource(R.string.common_ui_ok), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GestureSettingRow(
    label: String,
    selectedOption: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WinNativePanel)
                .border(1.dp, WinNativeOutline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = options.find { it.first == selectedOption }?.second ?: selectedOption,
                    color = WinNativeTextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = WinNativeTextSecondary, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WinNativeSurface).border(1.dp, WinNativeOutline)
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = WinNativeTextPrimary) },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureToggleSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    selectedOption: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = WinNativeTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = WinNativeAccent)
            )
        }
        if (checked) {
            Spacer(Modifier.height(4.dp))
            GestureSettingRow("", selectedOption, options, onOptionSelected)
        }
    }
}

@Composable
private fun getPanActionLabel(action: TouchGestureConfig.PanAction): String {
    return when (action) {
        TouchGestureConfig.PanAction.NONE -> stringResource(R.string.touch_gestures_action_none)
        TouchGestureConfig.PanAction.MIDDLE_CLICK -> stringResource(R.string.touch_gestures_action_middle_click)
        TouchGestureConfig.PanAction.WASD -> stringResource(R.string.touch_gestures_action_wasd)
        TouchGestureConfig.PanAction.ARROW_KEYS -> stringResource(R.string.touch_gestures_action_arrow_keys)
        TouchGestureConfig.PanAction.SCROLL -> stringResource(R.string.touch_gestures_action_scroll)
    }
}

@Composable
private fun getPinchActionLabel(action: TouchGestureConfig.PinchAction): String {
    return when (action) {
        TouchGestureConfig.PinchAction.NONE -> stringResource(R.string.touch_gestures_action_none)
        TouchGestureConfig.PinchAction.SCROLL_WHEEL -> stringResource(R.string.touch_gestures_action_scroll_wheel)
        TouchGestureConfig.PinchAction.ZOOM_KEYS -> stringResource(R.string.touch_gestures_action_zoom_keys)
    }
}

@Composable
private fun getTapActionLabel(action: TouchGestureConfig.TapAction): String {
    return when (action) {
        TouchGestureConfig.TapAction.NONE -> stringResource(R.string.touch_gestures_action_none)
        TouchGestureConfig.TapAction.LEFT_CLICK -> stringResource(R.string.touch_gestures_action_left_click)
        TouchGestureConfig.TapAction.RIGHT_CLICK -> stringResource(R.string.touch_gestures_action_right_click)
        TouchGestureConfig.TapAction.MIDDLE_CLICK -> stringResource(R.string.touch_gestures_action_middle_click)
    }
}
