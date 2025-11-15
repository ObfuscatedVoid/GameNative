package app.gamenative.ui.component.dialog

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.inputcontrols.ControlsProfile

/**
 * Controller binding presets and reset functionality
 *
 * Provides quick preset configurations for:
 * - Left/Right stick mappings (WASD, Arrows, Mouse, Gamepad)
 * - D-Pad mappings
 * - Reset to default gamepad bindings
 */


@Composable
internal fun BindingPresetsSection(
    profile: ControlsProfile,
    controllerId: String,
    onProfileUpdated: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Quickly configure common binding patterns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Compact grid layout for presets
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Left Stick row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "L",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(16.dp)
                        )
                        PresetButton(
                            text = "WASD",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.LEFT_STICK_WASD, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Arrows",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.LEFT_STICK_ARROWS, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Mouse",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.LEFT_STICK_MOUSE, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Gamepad",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.LEFT_STICK, onProfileUpdated)
                            },
                            compact = true
                        )
                    }

                    // Right Stick row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "R",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(16.dp)
                        )
                        PresetButton(
                            text = "WASD",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.RIGHT_STICK_WASD, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Arrows",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.RIGHT_STICK_ARROWS, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Mouse",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.RIGHT_STICK_MOUSE, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Gamepad",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.RIGHT_STICK, onProfileUpdated)
                            },
                            compact = true
                        )
                    }

                    // D-Pad row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.width(16.dp)
                        )
                        PresetButton(
                            text = "WASD",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.DPAD_WASD, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Arrows",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.DPAD_ARROWS, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Mouse",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.DPAD_MOUSE, onProfileUpdated)
                            },
                            compact = true
                        )
                        PresetButton(
                            text = "Gamepad",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                applyPreset(profile, controllerId, PresetType.DPAD, onProfileUpdated)
                            },
                            compact = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Reset to Default button (compact)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f))

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        showResetDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Reset to Default Gamepad", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Reset to Default dialog with checkboxes
    if (showResetDialog) {
        ResetToDefaultDialog(
            profile = profile,
            controllerId = controllerId,
            onDismiss = { showResetDialog = false },
            onReset = { resetLeftStick, resetRightStick, resetDPad, resetButtons ->
                // Apply the selected resets
                if (resetLeftStick) {
                    applyPreset(profile, controllerId, PresetType.LEFT_STICK, onProfileUpdated)
                }
                if (resetRightStick) {
                    applyPreset(profile, controllerId, PresetType.RIGHT_STICK, onProfileUpdated)
                }
                if (resetDPad) {
                    applyPreset(profile, controllerId, PresetType.DPAD, onProfileUpdated)
                }
                if (resetButtons) {
                    applyPreset(profile, controllerId, PresetType.BUTTONS, onProfileUpdated)
                }
                showResetDialog = false
            }
        )
    }
}

/**
 * Reset to Default dialog with checkboxes for selecting what to reset
 */

@Composable
internal fun ResetToDefaultDialog(
    profile: ControlsProfile,
    controllerId: String,
    onDismiss: () -> Unit,
    onReset: (Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var resetLeftStick by remember { mutableStateOf(true) }
    var resetRightStick by remember { mutableStateOf(true) }
    var resetDPad by remember { mutableStateOf(true) }
    var resetButtons by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset to Default Gamepad") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select which controls to reset to default gamepad bindings:",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Left Stick checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetLeftStick = !resetLeftStick },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Left Stick",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = resetLeftStick,
                        onCheckedChange = { resetLeftStick = it }
                    )
                }

                // Right Stick checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetRightStick = !resetRightStick },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Right Stick",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = resetRightStick,
                        onCheckedChange = { resetRightStick = it }
                    )
                }

                // D-Pad checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetDPad = !resetDPad },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "D-Pad",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = resetDPad,
                        onCheckedChange = { resetDPad = it }
                    )
                }

                // All Buttons checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetButtons = !resetButtons },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Buttons (A, B, X, Y, L1, R1, L2, R2, etc.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = resetButtons,
                        onCheckedChange = { resetButtons = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onReset(resetLeftStick, resetRightStick, resetDPad, resetButtons)
                },
                enabled = resetLeftStick || resetRightStick || resetDPad || resetButtons
            ) {
                Text("Reset Selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Preset types for binding configurations
 */
internal enum class PresetType {
    // Stick-specific presets
    LEFT_STICK_WASD, LEFT_STICK_ARROWS, LEFT_STICK_MOUSE,
    RIGHT_STICK_WASD, RIGHT_STICK_ARROWS, RIGHT_STICK_MOUSE,

    // D-Pad specific presets
    DPAD_WASD, DPAD_ARROWS, DPAD_MOUSE,

    // Gamepad presets
    DPAD, LEFT_STICK, RIGHT_STICK, BUTTONS,

    // Reset
    RESET_DEFAULT
}

/**
 * Apply a preset binding configuration
 */
internal fun applyPreset(
    profile: ControlsProfile,
    controllerId: String,
    presetType: PresetType,
    onProfileUpdated: () -> Unit
) {
    val controller = profile.getController(controllerId) ?: return

    // Define the mappings for each direction
    val mappings = when (presetType) {
        // LEFT STICK ONLY presets
        PresetType.LEFT_STICK_WASD -> listOf(
            -3 to com.winlator.inputcontrols.Binding.KEY_W,      // Left Stick Up -> W
            -4 to com.winlator.inputcontrols.Binding.KEY_S,      // Left Stick Down -> S
            -1 to com.winlator.inputcontrols.Binding.KEY_A,      // Left Stick Left -> A
            -2 to com.winlator.inputcontrols.Binding.KEY_D       // Left Stick Right -> D
        )
        PresetType.LEFT_STICK_ARROWS -> listOf(
            -3 to com.winlator.inputcontrols.Binding.KEY_UP,     // Left Stick Up -> Up Arrow
            -4 to com.winlator.inputcontrols.Binding.KEY_DOWN,   // Left Stick Down -> Down Arrow
            -1 to com.winlator.inputcontrols.Binding.KEY_LEFT,   // Left Stick Left -> Left Arrow
            -2 to com.winlator.inputcontrols.Binding.KEY_RIGHT   // Left Stick Right -> Right Arrow
        )
        PresetType.LEFT_STICK_MOUSE -> listOf(
            -3 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_UP,      // Left Stick Up -> Mouse Up
            -4 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_DOWN,    // Left Stick Down -> Mouse Down
            -1 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_LEFT,    // Left Stick Left -> Mouse Left
            -2 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_RIGHT    // Left Stick Right -> Mouse Right
        )
        // RIGHT STICK ONLY presets
        PresetType.RIGHT_STICK_WASD -> listOf(
            -7 to com.winlator.inputcontrols.Binding.KEY_W,      // Right Stick Up -> W
            -8 to com.winlator.inputcontrols.Binding.KEY_S,      // Right Stick Down -> S
            -5 to com.winlator.inputcontrols.Binding.KEY_A,      // Right Stick Left -> A
            -6 to com.winlator.inputcontrols.Binding.KEY_D       // Right Stick Right -> D
        )
        PresetType.RIGHT_STICK_ARROWS -> listOf(
            -7 to com.winlator.inputcontrols.Binding.KEY_UP,     // Right Stick Up -> Up Arrow
            -8 to com.winlator.inputcontrols.Binding.KEY_DOWN,   // Right Stick Down -> Down Arrow
            -5 to com.winlator.inputcontrols.Binding.KEY_LEFT,   // Right Stick Left -> Left Arrow
            -6 to com.winlator.inputcontrols.Binding.KEY_RIGHT   // Right Stick Right -> Right Arrow
        )
        PresetType.RIGHT_STICK_MOUSE -> listOf(
            -7 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_UP,      // Right Stick Up -> Mouse Up
            -8 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_DOWN,    // Right Stick Down -> Mouse Down
            -5 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_LEFT,    // Right Stick Left -> Mouse Left
            -6 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_RIGHT    // Right Stick Right -> Mouse Right
        )
        // D-PAD ONLY presets
        PresetType.DPAD_WASD -> listOf(
            19 to com.winlator.inputcontrols.Binding.KEY_W,      // D-Pad Up -> W
            20 to com.winlator.inputcontrols.Binding.KEY_S,      // D-Pad Down -> S
            21 to com.winlator.inputcontrols.Binding.KEY_A,      // D-Pad Left -> A
            22 to com.winlator.inputcontrols.Binding.KEY_D       // D-Pad Right -> D
        )
        PresetType.DPAD_ARROWS -> listOf(
            19 to com.winlator.inputcontrols.Binding.KEY_UP,     // D-Pad Up -> Up Arrow
            20 to com.winlator.inputcontrols.Binding.KEY_DOWN,   // D-Pad Down -> Down Arrow
            21 to com.winlator.inputcontrols.Binding.KEY_LEFT,   // D-Pad Left -> Left Arrow
            22 to com.winlator.inputcontrols.Binding.KEY_RIGHT   // D-Pad Right -> Right Arrow
        )
        PresetType.DPAD_MOUSE -> listOf(
            19 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_UP,      // D-Pad Up -> Mouse Up
            20 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_DOWN,    // D-Pad Down -> Mouse Down
            21 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_LEFT,    // D-Pad Left -> Mouse Left
            22 to com.winlator.inputcontrols.Binding.MOUSE_MOVE_RIGHT    // D-Pad Right -> Mouse Right
        )
        PresetType.DPAD -> listOf(
            19 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_UP,
            20 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_DOWN,
            21 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_LEFT,
            22 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_RIGHT,
            -3 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_UP,    // AXIS_Y_NEGATIVE
            -4 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_DOWN,  // AXIS_Y_POSITIVE
            -1 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_LEFT,  // AXIS_X_NEGATIVE
            -2 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_RIGHT  // AXIS_X_POSITIVE
        )
        PresetType.LEFT_STICK -> listOf(
            19 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_UP,
            20 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_DOWN,
            21 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_LEFT,
            22 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_RIGHT,
            -3 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_UP,     // AXIS_Y_NEGATIVE
            -4 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_DOWN,   // AXIS_Y_POSITIVE
            -1 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_LEFT,   // AXIS_X_NEGATIVE
            -2 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_RIGHT   // AXIS_X_POSITIVE
        )
        PresetType.RIGHT_STICK -> listOf(
            19 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_UP,
            20 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_DOWN,
            21 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_LEFT,
            22 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_RIGHT,
            -7 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_UP,     // AXIS_RZ_NEGATIVE
            -8 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_DOWN,   // AXIS_RZ_POSITIVE
            -5 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_LEFT,   // AXIS_Z_NEGATIVE
            -6 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_RIGHT   // AXIS_Z_POSITIVE
        )
        PresetType.BUTTONS -> listOf(
            // All Gamepad Buttons (A, B, X, Y, L1, R1, L2, R2, L3, R3, Start, Select)
            96 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_A,      // KEYCODE_BUTTON_A
            97 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_B,      // KEYCODE_BUTTON_B
            99 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_X,      // KEYCODE_BUTTON_X
            100 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_Y,     // KEYCODE_BUTTON_Y
            102 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L1,    // KEYCODE_BUTTON_L1
            103 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R1,    // KEYCODE_BUTTON_R1
            104 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L2,    // KEYCODE_BUTTON_L2
            105 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R2,    // KEYCODE_BUTTON_R2
            106 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L3,    // KEYCODE_BUTTON_THUMBL
            107 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R3,    // KEYCODE_BUTTON_THUMBR
            108 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_START, // KEYCODE_BUTTON_START
            109 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_SELECT // KEYCODE_BUTTON_SELECT
        )
        PresetType.RESET_DEFAULT -> listOf(
            // Buttons (matching InputControlsManager.addDefaultControllerBindings)
            96 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_A,      // KEYCODE_BUTTON_A
            97 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_B,      // KEYCODE_BUTTON_B
            99 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_X,      // KEYCODE_BUTTON_X
            100 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_Y,     // KEYCODE_BUTTON_Y
            102 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L1,    // KEYCODE_BUTTON_L1
            103 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R1,    // KEYCODE_BUTTON_R1
            104 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L2,    // KEYCODE_BUTTON_L2
            105 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R2,    // KEYCODE_BUTTON_R2
            106 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_L3,    // KEYCODE_BUTTON_THUMBL
            107 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_R3,    // KEYCODE_BUTTON_THUMBR
            108 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_START, // KEYCODE_BUTTON_START
            109 to com.winlator.inputcontrols.Binding.GAMEPAD_BUTTON_SELECT,// KEYCODE_BUTTON_SELECT
            // D-Pad
            19 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_UP,       // KEYCODE_DPAD_UP
            20 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_DOWN,     // KEYCODE_DPAD_DOWN
            21 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_LEFT,     // KEYCODE_DPAD_LEFT
            22 to com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_RIGHT,    // KEYCODE_DPAD_RIGHT
            // Left Stick - default to gamepad left stick bindings
            -3 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_UP,     // AXIS_Y_NEGATIVE
            -4 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_DOWN,   // AXIS_Y_POSITIVE
            -1 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_LEFT,   // AXIS_X_NEGATIVE
            -2 to com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_RIGHT,  // AXIS_X_POSITIVE
            // Right Stick - default to gamepad right stick bindings
            -7 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_UP,     // AXIS_RZ_NEGATIVE
            -8 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_DOWN,   // AXIS_RZ_POSITIVE
            -5 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_LEFT,   // AXIS_Z_NEGATIVE
            -6 to com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_RIGHT   // AXIS_Z_POSITIVE
        )
    }

    // Clear all existing bindings first for better performance (avoids O(n*m) complexity)
    controller.controllerBindings.clear()

    // Apply each mapping
    mappings.forEach { (keyCode, binding) ->
        // Add new binding
        val newBinding = com.winlator.inputcontrols.ExternalControllerBinding()
        newBinding.setKeyCode(keyCode)
        newBinding.binding = binding
        controller.controllerBindings.add(newBinding)
    }

    // Save and update
    profile.save()
    onProfileUpdated()
}


@Composable
internal fun PresetButton(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = if (compact) {
            PaddingValues(horizontal = 6.dp, vertical = 4.dp)
        } else {
            PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        }
    ) {
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
