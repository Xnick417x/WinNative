package com.winlator.cmod.runtime.input.ui

import com.winlator.cmod.runtime.input.controls.Binding
import org.json.JSONObject

data class TouchGestureConfig(
    var enabled: Boolean = false,
    var twoFingerPanAction: PanAction = PanAction.MIDDLE_CLICK,
    var pinchEnabled: Boolean = true,
    var pinchAction: PinchAction = PinchAction.SCROLL_WHEEL,
    var oneFingerLongPressAction: Binding = Binding.MOUSE_RIGHT_BUTTON,
    var twoFingerTapAction: Binding = Binding.MOUSE_RIGHT_BUTTON,
    var threeFingerTapAction: Binding = Binding.MOUSE_MIDDLE_BUTTON,
    var fourFingerTapAction: Binding = Binding.NONE,
    var threeFingerSwipeUpAction: Binding = Binding.NONE,
    var threeFingerSwipeDownAction: Binding = Binding.NONE,
    var threeFingerSwipeLeftAction: Binding = Binding.NONE,
    var threeFingerSwipeRightAction: Binding = Binding.NONE,
    var fourFingerSwipeUpAction: Binding = Binding.NONE,
    var fourFingerSwipeDownAction: Binding = Binding.NONE,
    var fourFingerSwipeLeftAction: Binding = Binding.NONE,
    var fourFingerSwipeRightAction: Binding = Binding.NONE
) {
    enum class PanAction {
        NONE,
        MIDDLE_CLICK,
        WASD,
        ARROW_KEYS,
        SCROLL
    }

    enum class PinchAction {
        NONE,
        SCROLL_WHEEL,
        ZOOM_KEYS
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("enabled", enabled)
        json.put("twoFingerPanAction", twoFingerPanAction.name)
        json.put("pinchEnabled", pinchEnabled)
        json.put("pinchAction", pinchAction.name)
        json.put("oneFingerLongPressAction", oneFingerLongPressAction.name)
        json.put("twoFingerTapAction", twoFingerTapAction.name)
        json.put("threeFingerTapAction", threeFingerTapAction.name)
        json.put("fourFingerTapAction", fourFingerTapAction.name)
        json.put("threeFingerSwipeUpAction", threeFingerSwipeUpAction.name)
        json.put("threeFingerSwipeDownAction", threeFingerSwipeDownAction.name)
        json.put("threeFingerSwipeLeftAction", threeFingerSwipeLeftAction.name)
        json.put("threeFingerSwipeRightAction", threeFingerSwipeRightAction.name)
        json.put("fourFingerSwipeUpAction", fourFingerSwipeUpAction.name)
        json.put("fourFingerSwipeDownAction", fourFingerSwipeDownAction.name)
        json.put("fourFingerSwipeLeftAction", fourFingerSwipeLeftAction.name)
        json.put("fourFingerSwipeRightAction", fourFingerSwipeRightAction.name)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject?): TouchGestureConfig {
            if (json == null) return TouchGestureConfig()
            val config = TouchGestureConfig()
            config.enabled = json.optBoolean("enabled", false)
            config.twoFingerPanAction = PanAction.valueOf(json.optString("twoFingerPanAction", PanAction.MIDDLE_CLICK.name))
            config.pinchEnabled = json.optBoolean("pinchEnabled", true)
            config.pinchAction = PinchAction.valueOf(json.optString("pinchAction", PinchAction.SCROLL_WHEEL.name))
            config.oneFingerLongPressAction = Binding.valueOf(json.optString("oneFingerLongPressAction", Binding.MOUSE_RIGHT_BUTTON.name))
            config.twoFingerTapAction = Binding.valueOf(json.optString("twoFingerTapAction", Binding.MOUSE_RIGHT_BUTTON.name))
            config.threeFingerTapAction = Binding.valueOf(json.optString("threeFingerTapAction", Binding.MOUSE_MIDDLE_BUTTON.name))
            config.fourFingerTapAction = Binding.valueOf(json.optString("fourFingerTapAction", Binding.NONE.name))
            config.threeFingerSwipeUpAction = Binding.valueOf(json.optString("threeFingerSwipeUpAction", Binding.NONE.name))
            config.threeFingerSwipeDownAction = Binding.valueOf(json.optString("threeFingerSwipeDownAction", Binding.NONE.name))
            config.threeFingerSwipeLeftAction = Binding.valueOf(json.optString("threeFingerSwipeLeftAction", Binding.NONE.name))
            config.threeFingerSwipeRightAction = Binding.valueOf(json.optString("threeFingerSwipeRightAction", Binding.NONE.name))
            config.fourFingerSwipeUpAction = Binding.valueOf(json.optString("fourFingerSwipeUpAction", Binding.NONE.name))
            config.fourFingerSwipeDownAction = Binding.valueOf(json.optString("fourFingerSwipeDownAction", Binding.NONE.name))
            config.fourFingerSwipeLeftAction = Binding.valueOf(json.optString("fourFingerSwipeLeftAction", Binding.NONE.name))
            config.fourFingerSwipeRightAction = Binding.valueOf(json.optString("fourFingerSwipeRightAction", Binding.NONE.name))
            return config
        }
    }
}
