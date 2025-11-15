package app.gamenative.ui.component.dialog

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager

/**
 * Physical Controller Configuration UI
 *
 * Displays and manages:
 * - Detected physical controllers
 * - Controller button bindings
 * - Sensitivity settings
 * - Quick presets for controller mappings
 */



@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhysicalControllerConfigSection(
    profile: ControlsProfile,
    onSwitchToOnScreen: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onProfileUpdated: () -> Unit,
    onRenameProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    // Use state to track controllers so UI updates when controllers are added/removed
    var controllers by remember { mutableStateOf(profile.getControllers()) }
    var selectedControllerId by remember { mutableStateOf(controllers.firstOrNull()?.id) }
    var showBindingDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // Refresh counter to force binding list recomposition when presets are applied
    var bindingsRefreshKey by remember { mutableStateOf(0) }

    // Refresh controllers list when profile is updated
    LaunchedEffect(profile) {
        controllers = profile.getControllers()
        if (selectedControllerId == null) {
            selectedControllerId = controllers.firstOrNull()?.id
        }
    }

    // Update selectedControllerId if controllers list changes and current selection is invalid
    LaunchedEffect(controllers) {
        if (selectedControllerId == null && controllers.isNotEmpty()) {
            selectedControllerId = controllers.first().id
        } else if (selectedControllerId != null && controllers.none { it.id == selectedControllerId }) {
            // Current selection no longer exists, select first available
            selectedControllerId = controllers.firstOrNull()?.id
        }
    }

    var showSensitivitySettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Fixed Header matching Controls Profiles style
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            actions = {
                // Edit profile name button
                IconButton(onClick = onRenameProfile) {
                    Icon(Icons.Default.Edit, "Rename Profile")
                }

                // Export button
                IconButton(onClick = {
                    val inputControlsManager = InputControlsManager(context)
                    val exportedFile = inputControlsManager.exportProfile(profile)
                    if (exportedFile != null) {
                        Toast.makeText(
                            context,
                            "Profile exported to:\n${exportedFile.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to export profile",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Icon(Icons.Default.FileDownload, "Export Profile")
                }

                // Switch to On-Screen Controls button
                IconButton(onClick = onSwitchToOnScreen) {
                    Icon(Icons.Default.TouchApp, "Switch to On-Screen Controls")
                }

                // Save button
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Check, "Save")
                }

                // Close button
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
        )

        // Content area with padding - scrollable (with side bars, not full width)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configure physical controller button mappings and sensitivity for this profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        // Sensitivity settings toggle button
        OutlinedButton(
            onClick = { showSensitivitySettings = !showSensitivitySettings },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (showSensitivitySettings) Icons.Default.Close else Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(if (showSensitivitySettings) "Hide Sensitivity Settings" else "Sensitivity Settings")
        }

        // Sensitivity settings for physical controller (collapsible)
        if (showSensitivitySettings) {
            app.gamenative.ui.component.settings.SensitivitySettingsSection(
                profile = profile,
                isPhysicalController = true,
                onSettingsChanged = {
                    onProfileUpdated()
                }
            )
        }

        // Show detected physical controllers
        DetectedControllersCard()

        // Controller selector or add button
        if (controllers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No controller configuration",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // Add default controller configuration
                            val controller = profile.addController("*")
                            controller.name = "Default Physical Controller"
                            profile.save()

                            // Update local state to refresh UI immediately
                            controllers = profile.getControllers()
                            selectedControllerId = controller.id

                            onProfileUpdated()
                        }
                    ) {
                        Text("Add Default Controller Config")
                    }
                }
            }
        } else {
            // Show controller info
            val selectedController = controllers.find { it.id == selectedControllerId } ?: controllers.first()

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Controller: ${selectedController.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ID: ${selectedController.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Presets Section
            BindingPresetsSection(
                profile = profile,
                controllerId = selectedController.id,
                onProfileUpdated = {
                    // Use in-memory controllers (already saved to disk by preset function)
                    controllers = profile.getControllers()
                    bindingsRefreshKey++
                    onProfileUpdated()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Show bindings - use key to force recomposition
            key(bindingsRefreshKey) {
                ControllerBindingsSection(
                    profile = profile,
                    controllerId = selectedController.id,
                    onBindingClick = { keyCode, buttonName ->
                        showBindingDialog = Pair(keyCode, buttonName)
                    },
                    onProfileUpdated = {
                        bindingsRefreshKey++
                        onProfileUpdated()
                    }
                )
            }
        }
        }

        // Binding dialog
        showBindingDialog?.let { (keyCode, buttonName) ->
            val controller = profile.getController(selectedControllerId ?: "*")
            val currentBinding = controller?.controllerBindings?.find {
                it.keyCodeForAxis == keyCode
            }?.binding

            ControllerBindingDialog(
                buttonName = buttonName,
                currentBinding = currentBinding,
                onDismiss = { showBindingDialog = null },
                onBindingSelected = { binding: com.winlator.inputcontrols.Binding? ->
                    controller?.let {
                        // Remove existing binding for this keyCode
                        it.controllerBindings.removeIf { b -> b.keyCodeForAxis == keyCode }

                        // Add new binding if not null
                        if (binding != null) {
                            val newBinding = com.winlator.inputcontrols.ExternalControllerBinding()
                            newBinding.setKeyCode(keyCode)
                            newBinding.binding = binding
                            it.controllerBindings.add(newBinding)
                        }

                        profile.save()
                        // Use in-memory controllers (already saved to disk)
                        controllers = profile.getControllers()
                        bindingsRefreshKey++
                        onProfileUpdated()
                    }
                    showBindingDialog = null
                }
            )
        }
    }
}

/**
 * Shows all button bindings for a controller
 */

@Composable
internal fun ControllerBindingsSection(
    profile: ControlsProfile,
    controllerId: String,
    onBindingClick: (Int, String) -> Unit,
    onProfileUpdated: () -> Unit
) {
    Text(
        text = "Button Mappings",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    val controller = profile.getController(controllerId)

    // Standard gamepad buttons
    val buttonMappings = listOf(
        96 to "A Button",
        97 to "B Button",
        98 to "X Button",
        99 to "Y Button",
        100 to "L1 (LB)",
        101 to "R1 (RB)",
        102 to "L2 (LT)",
        103 to "R2 (RT)",
        106 to "L3 (Left Stick Click)",
        107 to "R3 (Right Stick Click)",
        108 to "Select/Back",
        109 to "Start",
        19 to "D-Pad Up",
        20 to "D-Pad Down",
        21 to "D-Pad Left",
        22 to "D-Pad Right"
    )

    // Analog stick axes (matches ExternalControllerBinding constants)
    val analogMappings = listOf(
        -3 to "Left Stick Up",      // AXIS_Y_NEGATIVE
        -4 to "Left Stick Down",    // AXIS_Y_POSITIVE
        -1 to "Left Stick Left",    // AXIS_X_NEGATIVE
        -2 to "Left Stick Right",   // AXIS_X_POSITIVE
        -7 to "Right Stick Up",     // AXIS_RZ_NEGATIVE
        -8 to "Right Stick Down",   // AXIS_RZ_POSITIVE
        -5 to "Right Stick Left",   // AXIS_Z_NEGATIVE
        -6 to "Right Stick Right"   // AXIS_Z_POSITIVE
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        buttonMappings.forEach { (keyCode, buttonName) ->
            val binding = controller?.controllerBindings?.find { it.keyCodeForAxis == keyCode }

            BindingListItem(
                buttonName = buttonName,
                binding = binding?.binding,
                onClick = { onBindingClick(keyCode, buttonName) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Analog Sticks",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        analogMappings.forEach { (keyCode, buttonName) ->
            val binding = controller?.controllerBindings?.find { it.keyCodeForAxis == keyCode }

            BindingListItem(
                buttonName = buttonName,
                binding = binding?.binding,
                onClick = { onBindingClick(keyCode, buttonName) }
            )
        }
    }
}


@Composable
internal fun BindingListItem(
    buttonName: String,
    binding: com.winlator.inputcontrols.Binding?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buttonName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = binding?.toString() ?: "Not mapped",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (binding != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Shows detected physical controllers
 */

@Composable
internal fun DetectedControllersCard() {
    // Get detected physical controllers
    val detectedControllers = remember {
        com.winlator.inputcontrols.ExternalController.getControllers()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (detectedControllers.isNotEmpty()) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Detected Physical Controllers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (detectedControllers.isEmpty()) {
                Text(
                    text = "No physical controllers detected. Please connect a controller.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                detectedControllers.forEach { controller ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = controller.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Device ID: ${controller.deviceId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
