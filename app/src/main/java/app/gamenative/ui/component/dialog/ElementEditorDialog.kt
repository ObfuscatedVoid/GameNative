package app.gamenative.ui.component.dialog

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.component.settings.SettingsTextField
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.winlator.inputcontrols.ControlElement
import com.winlator.widget.InputControlsView

/**
 * Compose-based element editor dialog matching the app's settings design pattern.
 *
 * Features:
 * - Full-width dialog at bottom of screen
 * - When adjusting size, dialog minimizes to show only slider controls
 * - Live preview of all changes on the actual control element
 * - Reset button returns size to 1.0x default
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementEditorDialog(
    element: ControlElement,
    view: InputControlsView,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    // Store original values for cancel/restore
    val originalScale by remember { mutableFloatStateOf(element.scale) }
    val originalText by remember { mutableStateOf(element.text) } // Keep null as null
    val originalType by remember { mutableStateOf(element.type) }
    val originalShape by remember { mutableStateOf(element.shape) }

    // Store original bindings for restore on cancel
    val originalBindings by remember {
        mutableStateOf(
            (0 until 4).map { element.getBindingAt(it) }
        )
    }

    // Track if there are unsaved changes
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Get the display text (either custom text or binding-based text)
    // Match the logic from ControlElement.getDisplayText()
    val initialDisplayText = remember(element) {
        val customText = element.text
        if (!customText.isNullOrEmpty()) {
            customText
        } else {
            // Show what's actually displayed (based on first binding)
            val binding = element.getBindingAt(0)
            if (binding != null && binding != com.winlator.inputcontrols.Binding.NONE) {
                var text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "")
                if (text.length > 7) {
                    // Abbreviate long binding names (e.g., "KEY A B" -> "KAB")
                    val parts = text.split(" ")
                    val sb = StringBuilder()
                    for (part in parts) {
                        if (part.isNotEmpty()) sb.append(part[0])
                    }
                    text = (if (binding.isMouse) "M" else "") + sb.toString()
                }
                text
            } else {
                ""
            }
        }
    }

    // Current editing values with live preview
    var currentScale by remember { mutableFloatStateOf(element.scale) }
    var currentText by remember { mutableStateOf(initialDisplayText) }
    var showBindingsEditor by remember { mutableStateOf(false) }
    var bindingSlotToEdit by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // Force recomposition of bindings section when bindings change
    var bindingsRefreshKey by remember { mutableIntStateOf(0) }

    // Track current dropdown selections
    var currentTypeIndex by remember { mutableIntStateOf(element.type.ordinal) }
    var currentShapeIndex by remember { mutableIntStateOf(element.shape.ordinal) }

    // State for size adjustment mode
    var showSizeAdjuster by remember { mutableStateOf(false) }

    // Get types array for saving
    val types = remember { ControlElement.Type.values() }

    // Apply changes to element for live preview
    LaunchedEffect(currentScale) {
        element.setScale(currentScale)
        view.invalidate()
    }

    // Only update element text if user has actually modified it
    // Don't apply preview for initial display text
    // Debounce text changes to avoid excessive redraws (500ms delay)
    LaunchedEffect(currentText) {
        // Only set custom text if user has explicitly modified it from the initial value
        // AND it's not empty (empty should remain null for binding-based display)
        if (currentText != initialDisplayText && currentText.isNotEmpty()) {
            kotlinx.coroutines.delay(500) // Debounce 500ms
            element.setText(currentText)
            view.invalidate()
        }
    }

    Dialog(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                showExitConfirmation = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        // Show either full settings dialog or minimized size adjuster
        if (showSizeAdjuster) {
            // Minimized size adjuster mode
            SizeAdjusterOverlay(
                element = element,
                view = view,
                currentScale = currentScale,
                onScaleChange = { currentScale = it },
                onConfirm = {
                    showSizeAdjuster = false
                },
                onCancel = {
                    currentScale = originalScale
                    element.setScale(originalScale)
                    view.invalidate()
                    showSizeAdjuster = false
                },
                onReset = {
                    currentScale = 1.0f
                    element.setScale(1.0f)
                    view.invalidate()
                }
            )
        } else {
            // Full settings dialog
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Edit ${element.type.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pos: (${element.x}, ${element.y}) • Size: ${String.format("%.2f", currentScale)}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (hasUnsavedChanges) {
                                showExitConfirmation = true
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Save changes to element
                            element.setScale(currentScale)
                            // If currentText is empty string, set to null to use binding-based text
                            // Otherwise use the current text value
                            element.setText(if (currentText.isEmpty()) null else currentText)
                            // Only change type if it's different (setType() calls reset() which clears bindings!)
                            if (element.type != types[currentTypeIndex]) {
                                element.type = types[currentTypeIndex]
                            }

                            // Save to disk
                            view.profile?.save()

                            // Log for debugging
                            Log.d("ElementEditorDialog", "Saved element ${element.type}: bindings = [${element.getBindingAt(0)?.name}, ${element.getBindingAt(1)?.name}, ${element.getBindingAt(2)?.name}, ${element.getBindingAt(3)?.name}]")

                            // Update canvas to show new bindings
                            view.invalidate()

                            // Mark as saved
                            hasUnsavedChanges = false

                            // Close dialog
                            onSave()
                        }) {
                            Icon(Icons.Default.Save, "Save")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Appearance Section
                SettingsGroup(title = { Text("Appearance") }) {
                    // Scale/Size - click to enter adjustment mode
                    SettingsMenuLink(
                        colors = settingsTileColors(),
                        title = { Text("Size") },
                        subtitle = { Text("${String.format("%.2f", currentScale)}x scale - Tap to adjust or copy from another element") },
                        onClick = {
                            showSizeAdjuster = true
                        }
                    )

                    // Text/Label - only for BUTTON type
                    if (element.type == ControlElement.Type.BUTTON) {
                        SettingsTextField(
                            colors = settingsTileColors(),
                            title = { Text("Label Text") },
                            subtitle = { Text("Custom text displayed on element") },
                            value = currentText,
                            onValueChange = { currentText = it },
                            action = {
                                // Reset button to restore original text
                                IconButton(onClick = {
                                    // Reset to initial display text (binding-based or original custom text)
                                    currentText = initialDisplayText
                                    element.setText(originalText)
                                    view.invalidate()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset to original",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }

                    // Element Type
                    val types = ControlElement.Type.values()
                    val typeNames = types.map { it.name.replace("_", " ") }
                    SettingsListDropdown(
                        colors = settingsTileColors(),
                        title = { Text("Element Type") },
                        subtitle = { Text("Change the element's behavior and appearance") },
                        value = currentTypeIndex,
                        items = typeNames,
                        onItemSelected = { index ->
                            currentTypeIndex = index
                            element.type = types[index]
                            view.invalidate()
                        }
                    )

                    // Element Shape (with restrictions)
                    // STICK, TRACKPAD, RANGE_BUTTON have fixed rendering shapes
                    // D_PAD uses custom cross-shaped path and doesn't respect shape
                    val availableShapes = when (element.type) {
                        ControlElement.Type.STICK -> {
                            // Stick is always rendered as CIRCLE
                            listOf(ControlElement.Shape.CIRCLE)
                        }
                        ControlElement.Type.TRACKPAD,
                        ControlElement.Type.RANGE_BUTTON -> {
                            // Trackpad and Range Button are always rendered as ROUND_RECT
                            listOf(ControlElement.Shape.ROUND_RECT)
                        }
                        ControlElement.Type.D_PAD -> {
                            // D-Pad uses custom cross-shaped path, shape doesn't affect rendering
                            // Don't allow changing shape
                            listOf(element.shape)
                        }
                        ControlElement.Type.BUTTON -> {
                            // Buttons fully support all shapes
                            ControlElement.Shape.values().toList()
                        }
                        else -> ControlElement.Shape.values().toList()
                    }

                    if (availableShapes.size > 1) {
                        val shapeNames = availableShapes.map { it.name.replace("_", " ") }
                        val currentShapeIndexInList = availableShapes.indexOf(element.shape).coerceAtLeast(0)
                        SettingsListDropdown(
                            colors = settingsTileColors(),
                            title = { Text("Shape") },
                            subtitle = { Text("Visual style of the element") },
                            value = currentShapeIndexInList,
                            items = shapeNames,
                            onItemSelected = { index ->
                                element.shape = availableShapes[index]
                                view.invalidate()
                            }
                        )
                    } else if (availableShapes.size == 1 && element.type != ControlElement.Type.D_PAD) {
                        // Show info for restricted types (but not D-PAD since it's obvious)
                        SettingsMenuLink(
                            colors = settingsTileColors(),
                            title = { Text("Shape") },
                            subtitle = { Text("${element.type.name} can only use ${availableShapes[0].name.replace("_", " ")} shape") },
                            enabled = false,
                            onClick = {}
                        )
                    }
                }

                // Bindings Section
                // Use key() with bindingsRefreshKey to force recomposition when bindings change
                key(bindingsRefreshKey) {
                    SettingsGroup(title = { Text("Bindings") }) {
                        // Quick Presets for directional controls (D-Pad and Stick only)
                        if (element.type == ControlElement.Type.D_PAD || element.type == ControlElement.Type.STICK) {
                            VirtualControlPresets(
                                element = element,
                                view = view,
                                onPresetsApplied = {
                                    // Mark as having unsaved changes
                                    hasUnsavedChanges = true
                                    // Force UI refresh after presets are applied
                                    bindingsRefreshKey++
                                }
                            )
                        }

                        if (element.type == ControlElement.Type.RANGE_BUTTON) {
                            SettingsMenuLink(
                                colors = settingsTileColors(),
                                title = { Text("Bindings (Auto-Generated)") },
                                subtitle = { Text("Range Button bindings are generated from Range setting") },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            val bindingCount = when (element.type) {
                                ControlElement.Type.BUTTON -> 2
                                else -> 4
                            }

                            for (i in 0 until bindingCount) {
                                val binding = element.getBindingAt(i)
                                val bindingName = binding?.toString() ?: "NONE"

                                val slotLabel = when (element.type) {
                                    ControlElement.Type.BUTTON -> {
                                        if (i == 0) "Primary Action" else "Secondary Action"
                                    }
                                    ControlElement.Type.D_PAD,
                                    ControlElement.Type.STICK -> {
                                        listOf("Up", "Right", "Down", "Left")[i]
                                    }
                                    ControlElement.Type.TRACKPAD -> {
                                        listOf("Up", "Right", "Down", "Left")[i] + " (Mouse)"
                                    }
                                    else -> "Slot ${i + 1}"
                                }

                                SettingsMenuLink(
                                    colors = settingsTileColors(),
                                    title = { Text(slotLabel) },
                                    subtitle = { Text(bindingName) },
                                    onClick = {
                                        bindingSlotToEdit = Pair(i, slotLabel)
                                    }
                                )
                            }

                            // Helper text for buttons
                            if (element.type == ControlElement.Type.BUTTON) {
                                Text(
                                    text = "Both slots are pressed simultaneously. Use secondary for modifier keys (Shift, Ctrl, Alt).",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                // Properties Section
                SettingsGroup(title = { Text("Properties") }) {
                    SettingsMenuLink(
                        colors = settingsTileColors(),
                        title = { Text("Position") },
                        subtitle = { Text("X: ${element.x}, Y: ${element.y}") },
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }
        }
    }

    // Show binding selector dialog
    bindingSlotToEdit?.let { (slotIndex, slotLabel) ->
        val currentBinding = element.getBindingAt(slotIndex)

        ControllerBindingDialog(
            buttonName = slotLabel,
            currentBinding = currentBinding,
            onDismiss = { bindingSlotToEdit = null },
            onBindingSelected = { binding ->
                Log.d("ElementEditorDialog", "Binding changed for element ${element.type} slot $slotIndex ($slotLabel): ${currentBinding?.name} -> ${binding?.name ?: "NONE"}")

                // Update binding in memory only (not saved to disk yet)
                if (binding != null) {
                    element.setBindingAt(slotIndex, binding)
                } else {
                    element.setBindingAt(slotIndex, com.winlator.inputcontrols.Binding.NONE)
                }

                // If this is a button and slot 0 (primary), update label text to match new binding
                // (ControlElement.getDisplayText() returns custom text if set, otherwise binding text)
                if (element.type == ControlElement.Type.BUTTON && slotIndex == 0) {
                    // Check if custom text is empty or same as old binding text
                    val customText = element.text
                    if (customText.isNullOrEmpty() || customText == currentBinding?.toString()?.replace("NUMPAD ", "NP")?.replace("BUTTON ", "")) {
                        // Clear custom text so new binding text will show
                        element.setText(null)

                        // Update currentText state to show what will actually be displayed (new binding text)
                        val newBindingText = binding?.toString()?.replace("NUMPAD ", "NP")?.replace("BUTTON ", "") ?: ""
                        currentText = if (newBindingText.length > 7) {
                            // Abbreviate long names to match getDisplayText() logic
                            val parts = newBindingText.split(" ")
                            val sb = StringBuilder()
                            for (part in parts) {
                                if (part.isNotEmpty()) sb.append(part[0])
                            }
                            (if (binding?.isMouse() == true) "M" else "") + sb.toString()
                        } else {
                            newBindingText
                        }
                    }
                }

                // Mark as having unsaved changes
                hasUnsavedChanges = true

                // Update canvas to show new binding immediately
                view.invalidate()

                // Close binding selector
                bindingSlotToEdit = null

                // Force UI refresh to show updated binding in list
                bindingsRefreshKey++
            }
        )
    }

    // Show exit confirmation dialog if there are unsaved changes
    if (showExitConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save before closing?") },
            confirmButton = {
                TextButton(onClick = {
                    // Save and close
                    element.setScale(currentScale)
                    element.setText(if (currentText.isEmpty()) null else currentText)
                    // Only change type if it's different (setType() calls reset() which clears bindings!)
                    if (element.type != types[currentTypeIndex]) {
                        element.type = types[currentTypeIndex]
                    }
                    view.profile?.save()
                    view.invalidate()
                    showExitConfirmation = false
                    onDismiss()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Discard changes and close
                    element.setScale(originalScale)
                    element.setText(originalText)
                    element.type = originalType
                    element.shape = originalShape
                    // Restore original bindings
                    originalBindings.forEachIndexed { index, binding ->
                        if (binding != null) {
                            element.setBindingAt(index, binding)
                        }
                    }
                    view.invalidate()
                    showExitConfirmation = false
                    onDismiss()
                }) {
                    Text("Discard")
                }
            }
        )
    }
}

/**
 * Floating size adjuster overlay - appears on top of the controls view
 * with a slider and action buttons positioned away from the control element.
 * Automatically positions itself at top or bottom based on element location.
 */
@Composable
private fun SizeAdjusterOverlay(
    element: ControlElement,
    view: InputControlsView,
    currentScale: Float,
    onScaleChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    // Determine if element is in top or bottom half of screen
    // Coordinates are in actual screen pixels (not normalized)
    // Y=0 is at TOP, Y increases downward (standard Android Canvas)
    val elementY = element.y
    val screenHeight = view.height.toFloat()

    // Use 60% threshold: if Y < 60% of screen height, element is in top portion, show slider at bottom
    // Otherwise element is in bottom 40%, show slider at top
    val isElementInTopPortion = elementY < (screenHeight * 0.6f)

    // Debug log
    android.util.Log.d("ElementEditor", "Element Y: $elementY, Screen Height: $screenHeight, ${(elementY/screenHeight*100).toInt()}% from top, showing slider at ${if (isElementInTopPortion) "BOTTOM" else "TOP"}")

    // Full screen transparent overlay
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Position slider opposite to element location - more compact and transparent
        Surface(
            modifier = Modifier
                .wrapContentHeight()
                .widthIn(max = 400.dp)
                .align(if (isElementInTopPortion) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title and current scale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adjust Size",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${String.format("%.2f", currentScale)}x",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                // Slider - more compact
                Slider(
                    value = currentScale,
                    onValueChange = onScaleChange,
                    valueRange = 0.1f..5.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )

                // All 4 buttons in one row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy Size button
                    OutlinedButton(
                        onClick = {
                            val context = view.context
                            showCopySizeDialog(context, element, view) { newScale ->
                                onScaleChange(newScale)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }

                    // Reset button
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }

                    // Confirm button
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Done", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Show dialog to copy size from another element
 */
private fun showCopySizeDialog(
    context: android.content.Context,
    currentElement: ControlElement,
    view: InputControlsView,
    onSizeCopied: (Float) -> Unit
) {
    val profile = view.profile ?: return
    val elements = profile.getElements()

    // Filter out the current element and create display list
    val otherElements = elements.filter { it != currentElement }

    if (otherElements.isEmpty()) {
        android.widget.Toast.makeText(
            context,
            "No other elements to copy from",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }

    // Create display items showing element info with better formatting
    val elementNames = otherElements.map { element ->
        val typeStr = element.type.name.replace("_", " ")
        val scaleStr = String.format("%.2fx", element.scale)

        // Get display text/binding
        val label = if (!element.text.isNullOrEmpty()) {
            element.text
        } else {
            val binding = element.getBindingAt(0)
            if (binding != null && binding != com.winlator.inputcontrols.Binding.NONE) {
                binding.toString().take(15)
            } else {
                "No Binding"
            }
        }

        // Format: "BUTTON • 1.50x • Space"
        "$typeStr • $scaleStr • $label"
    }.toTypedArray()

    android.app.AlertDialog.Builder(context)
        .setTitle("Copy Size From Element")
        .setItems(elementNames) { _, which ->
            val selectedElement = otherElements[which]
            onSizeCopied(selectedElement.scale)
            android.widget.Toast.makeText(
                context,
                "Copied size: ${String.format("%.2f", selectedElement.scale)}x",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Quick preset buttons for virtual control bindings
 */
@Composable
private fun VirtualControlPresets(
    element: ControlElement,
    view: InputControlsView,
    onPresetsApplied: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Quick Presets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Keyboard layouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.WASD, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("WASD", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.ARROW_KEYS, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Arrows", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.MOUSE_MOVE, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Mouse", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Gamepad modes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.DPAD, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("D-Pad", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.LEFT_STICK, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("L-Stick", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        applyVirtualPreset(element, VirtualPresetType.RIGHT_STICK, view)
                        onPresetsApplied()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("R-Stick", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Preset types for virtual control bindings
 */
private enum class VirtualPresetType {
    WASD, ARROW_KEYS, MOUSE_MOVE, DPAD, LEFT_STICK, RIGHT_STICK
}

/**
 * Apply a preset binding to a virtual control element
 */
private fun applyVirtualPreset(
    element: ControlElement,
    presetType: VirtualPresetType,
    view: InputControlsView
) {
    // Define bindings for each preset (Up, Right, Down, Left order)
    val bindings = when (presetType) {
        VirtualPresetType.WASD -> listOf(
            com.winlator.inputcontrols.Binding.KEY_W,
            com.winlator.inputcontrols.Binding.KEY_D,
            com.winlator.inputcontrols.Binding.KEY_S,
            com.winlator.inputcontrols.Binding.KEY_A
        )
        VirtualPresetType.ARROW_KEYS -> listOf(
            com.winlator.inputcontrols.Binding.KEY_UP,
            com.winlator.inputcontrols.Binding.KEY_RIGHT,
            com.winlator.inputcontrols.Binding.KEY_DOWN,
            com.winlator.inputcontrols.Binding.KEY_LEFT
        )
        VirtualPresetType.MOUSE_MOVE -> listOf(
            com.winlator.inputcontrols.Binding.MOUSE_MOVE_UP,
            com.winlator.inputcontrols.Binding.MOUSE_MOVE_RIGHT,
            com.winlator.inputcontrols.Binding.MOUSE_MOVE_DOWN,
            com.winlator.inputcontrols.Binding.MOUSE_MOVE_LEFT
        )
        VirtualPresetType.DPAD -> listOf(
            com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_UP,
            com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_RIGHT,
            com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_DOWN,
            com.winlator.inputcontrols.Binding.GAMEPAD_DPAD_LEFT
        )
        VirtualPresetType.LEFT_STICK -> listOf(
            com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_UP,
            com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_RIGHT,
            com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_DOWN,
            com.winlator.inputcontrols.Binding.GAMEPAD_LEFT_THUMB_LEFT
        )
        VirtualPresetType.RIGHT_STICK -> listOf(
            com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_UP,
            com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_RIGHT,
            com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_DOWN,
            com.winlator.inputcontrols.Binding.GAMEPAD_RIGHT_THUMB_LEFT
        )
    }

    // Apply bindings to element in memory only (not saved to disk yet)
    Log.d("ElementEditorDialog", "Applying preset $presetType to element ${element.type}")
    bindings.forEachIndexed { index, binding ->
        Log.d("ElementEditorDialog", "  Slot $index: ${element.getBindingAt(index)?.name} -> ${binding.name}")
        element.setBindingAt(index, binding)
    }

    // Update canvas to show new bindings immediately
    view.invalidate()
    Log.d("ElementEditorDialog", "Preset applied (not saved to disk yet)")
}
