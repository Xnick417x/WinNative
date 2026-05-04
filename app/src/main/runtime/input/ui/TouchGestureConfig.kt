package com.winlator.cmod.runtime.input.ui

import org.json.JSONObject

data class TouchGestureConfig(
    var enabled: Boolean = false,
    var twoFingerPanAction: PanAction = PanAction.MIDDLE_CLICK,
    var pinchEnabled: Boolean = true,
    var pinchAction: PinchAction = PinchAction.SCROLL_WHEEL,
    var threeFingerTapAction: TapAction = TapAction.NONE,
    var fourFingerTapAction: TapAction = TapAction.NONE
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

    enum class TapAction {
        NONE,
        LEFT_CLICK,
        RIGHT_CLICK,
        MIDDLE_CLICK
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("enabled", enabled)
        json.put("twoFingerPanAction", twoFingerPanAction.name)
        json.put("pinchEnabled", pinchEnabled)
        json.put("pinchAction", pinchAction.name)
        json.put("threeFingerTapAction", threeFingerTapAction.name)
        json.put("fourFingerTapAction", fourFingerTapAction.name)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject?): TouchGestureConfig {
            if (json == null) return TouchGestureConfig()
            return TouchGestureConfig(
                enabled = json.optBoolean("enabled", false),
                twoFingerPanAction = PanAction.valueOf(json.optString("twoFingerPanAction", PanAction.MIDDLE_CLICK.name)),
                pinchEnabled = json.optBoolean("pinchEnabled", true),
                pinchAction = PinchAction.valueOf(json.optString("pinchAction", PinchAction.SCROLL_WHEEL.name)),
                threeFingerTapAction = TapAction.valueOf(json.optString("threeFingerTapAction", TapAction.NONE.name)),
                fourFingerTapAction = TapAction.valueOf(json.optString("fourFingerTapAction", TapAction.NONE.name))
            )
        }
    }
}
