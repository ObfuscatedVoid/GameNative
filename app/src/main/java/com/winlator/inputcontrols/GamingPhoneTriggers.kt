package com.winlator.inputcontrols

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import androidx.annotation.StringRes
import app.gamenative.R

/**
 * Detection and registry for gaming phone built-in shoulder triggers.
 *
 * These devices emit regular keyboard keycodes (e.g. F7/F8) rather than standard gamepad
 * keycodes. This object identifies them so they can be routed through the controller binding system.
 */
object GamingPhoneTriggers {

    const val CONTROLLER_ID = "__device_triggers__"
    const val CONTROLLER_NAME = "Device Triggers"

    data class TriggerDef(val keyCode: Int, @get:StringRes val labelResId: Int, val defaultBinding: Binding)

    val KNOWN_TRIGGERS: List<TriggerDef> = listOf(
        TriggerDef(KeyEvent.KEYCODE_F7, R.string.left_trigger, Binding.GAMEPAD_BUTTON_L2),
        TriggerDef(KeyEvent.KEYCODE_F8, R.string.right_trigger, Binding.GAMEPAD_BUTTON_R2),
    )

    val triggerKeyCodes: Set<Int> = KNOWN_TRIGGERS.map { it.keyCode }.toSet()
    private val triggerKeyCodesArray: IntArray = triggerKeyCodes.toIntArray()

    fun isTriggerKeyCode(keyCode: Int): Boolean = keyCode in triggerKeyCodes

    /**
     * Scans connected InputDevices for built-in trigger capability using [InputDevice.hasKeys],
     * avoiding a hardcoded manufacturer/model list.
     */
    fun detectTriggerDevice(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isTriggerSourceDevice(device)) {
                val hasKeys = device.hasKeys(*triggerKeyCodesArray)
                if (hasKeys.all { it }) return true
            }
        }
        return false
    }

    /**
     * Heuristic for identifying a gaming phone's built-in trigger sensor:
     * - Not virtual, not an alphabetic keyboard
     * - Has SOURCE_KEYBOARD (trigger devices report as keyboard)
     * - Not a gamepad/joystick (handled by ExternalController)
     * - On API 29+, must be internal (filters USB/BT keyboards)
     */
    private fun isTriggerSourceDevice(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        if (device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) return false

        val sources = device.sources
        val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        if (!isKeyboard) return false

        val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isGamepad || isJoystick) return false

        // isExternal unavailable on API 26-28; non-alphabetic external keyboards (e.g. macro keypads)
        // could be misidentified on those versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (device.isExternal) return false
        }

        return true
    }
}
