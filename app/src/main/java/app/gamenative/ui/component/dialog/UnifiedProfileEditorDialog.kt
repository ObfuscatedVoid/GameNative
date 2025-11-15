package app.gamenative.ui.component.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.isActive
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import app.gamenative.R
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.enums.Orientation
import java.util.EnumSet

/**
 * Unified Profile Editor Dialog
 *
 * Allows editing both:
 * 1. Virtual controls (on-screen elements via InputControlsView)
 * 2. Physical controller bindings (via controller configuration UI)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedProfileEditorDialog(
    profile: ControlsProfile,
    initialTab: Int = 0,
    container: com.winlator.container.Container? = null, // Optional - used to detect if in-game or settings
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val isInGame = container != null // If container is provided, we're in-game
    var selectedTab by remember { mutableStateOf(initialTab) }
    var inputControlsView by remember { mutableStateOf<InputControlsView?>(null) }
    var showElementEditor by remember { mutableStateOf<com.winlator.inputcontrols.ControlElement?>(null) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showFloatingToolbar by remember { mutableStateOf(true) }
    var isToolbarExpanded by remember { mutableStateOf(true) }
    var selectedElement by remember { mutableStateOf<com.winlator.inputcontrols.ControlElement?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Undo/Redo functionality - stores element position snapshots
    data class ElementSnapshot(val element: com.winlator.inputcontrols.ControlElement, val x: Int, val y: Int, val scale: Float)
    val undoStack = remember { mutableStateListOf<ElementSnapshot>() }
    val redoStack = remember { mutableStateListOf<ElementSnapshot>() }
    val maxStackSize = 20
    var isPerformingUndoRedo by remember { mutableStateOf(false) }

    // Enforce allowed orientations based on context and tab
    // - On-screen controls (tab 0): Always respect user's orientation settings from PrefManager
    // - Physical controller editor (tab 1): Never lock orientation, allow free rotation
    DisposableEffect(selectedTab) {
        if (selectedTab == 0) {
            // Editing on-screen controls - apply user's orientation lock settings
            PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(PrefManager.allowedOrientation))
        } else {
            // Physical controller editor - unlock orientation, allow any rotation
            PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(EnumSet.of(Orientation.UNSPECIFIED))) // Allow all orientations
        }

        // ALWAYS hide system UI (status bar and navigation bar) for immersive editing experience
        // This applies to both on-screen controls AND physical controller editing
        PluviaApp.events.emit(AndroidEvent.SetSystemUIVisibility(false))

        onDispose {
            // Clear inputControlsView reference to prevent memory leaks
            inputControlsView = null

            // Restore system UI only when exiting from settings context
            // When in-game, keep system UI hidden (it was already hidden before opening editor)
            if (!isInGame) {
                PluviaApp.events.emit(AndroidEvent.SetSystemUIVisibility(true))
            }

            // When exiting the editor, reset orientation based on context
            if (!isInGame) {
                // In settings - reset to portrait only
                PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(EnumSet.of(Orientation.PORTRAIT)))
            }
            // If in-game, the XServerScreen will manage orientation when this dialog closes
        }
    }

    // Track element state changes for undo
    var lastTrackedElement by remember { mutableStateOf<com.winlator.inputcontrols.ControlElement?>(null) }
    var lastTrackedX by remember { mutableStateOf<Int?>(null) }
    var lastTrackedY by remember { mutableStateOf<Int?>(null) }
    var lastTrackedScale by remember { mutableStateOf<Float?>(null) }

    // Position from previous poll cycle (used to detect movement between polls)
    var lastSeenX by remember { mutableStateOf<Int?>(null) }
    var lastSeenY by remember { mutableStateOf<Int?>(null) }
    var lastSeenScale by remember { mutableStateOf<Float?>(null) }

    // Floating toolbar position state for on-screen controls - initialized to center top
    // Calculate center position: (screenWidth - toolbarWidth) / 2
    // Toolbar width is max 280dp, so we offset by -140dp from center
    val screenWidthPx = configuration.screenWidthDp * context.resources.displayMetrics.density
    val toolbarWidthPx = 280 * context.resources.displayMetrics.density
    val initialOffsetX = (screenWidthPx - toolbarWidthPx) / 2
    val initialOffsetY = 16f // 16px from top for a cleaner look

    var toolbarOffsetX by remember { mutableStateOf(initialOffsetX) }
    var toolbarOffsetY by remember { mutableStateOf(initialOffsetY) }

    // Load profile fresh each time
    val currentProfile = remember(profile.id) {
        InputControlsManager(context).getProfile(profile.id) ?: profile
    }

    // Function to save element state for undo
    fun saveElementStateForUndo(element: com.winlator.inputcontrols.ControlElement, x: Int? = null, y: Int? = null, scale: Float? = null) {
        val snapshot = ElementSnapshot(
            element,
            x ?: element.x.toInt(),
            y ?: element.y.toInt(),
            scale ?: element.scale
        )
        undoStack.add(snapshot)
        // Clear redo stack when new change is made (can't redo after new action)
        redoStack.clear()
        // Limit stack size
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
    }

    // Function to perform undo
    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            isPerformingUndoRedo = true
            val snapshot = undoStack.removeAt(undoStack.size - 1)

            // Save current state to redo stack before undoing
            val currentState = ElementSnapshot(
                snapshot.element,
                snapshot.element.x.toInt(),
                snapshot.element.y.toInt(),
                snapshot.element.scale
            )
            redoStack.add(currentState)
            if (redoStack.size > maxStackSize) {
                redoStack.removeAt(0)
            }

            // Apply the undo
            snapshot.element.setX(snapshot.x)
            snapshot.element.setY(snapshot.y)
            snapshot.element.setScale(snapshot.scale)

            currentProfile.save()
            inputControlsView?.invalidate()

            // Reset flag immediately and update tracking state
            // We do this synchronously to ensure the tracking coroutine sees the correct state
            isPerformingUndoRedo = false

            // Update BOTH tracking baseline and last seen position to prevent re-tracking
            lastTrackedElement = snapshot.element
            lastTrackedX = snapshot.x
            lastTrackedY = snapshot.y
            lastTrackedScale = snapshot.scale
            lastSeenX = snapshot.x
            lastSeenY = snapshot.y
            lastSeenScale = snapshot.scale
        }
    }

    // Function to perform redo
    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            isPerformingUndoRedo = true
            val snapshot = redoStack.removeAt(redoStack.size - 1)

            // Save current state to undo stack before redoing
            val currentState = ElementSnapshot(
                snapshot.element,
                snapshot.element.x.toInt(),
                snapshot.element.y.toInt(),
                snapshot.element.scale
            )
            undoStack.add(currentState)
            if (undoStack.size > maxStackSize) {
                undoStack.removeAt(0)
            }

            // Apply the redo
            snapshot.element.setX(snapshot.x)
            snapshot.element.setY(snapshot.y)
            snapshot.element.setScale(snapshot.scale)

            currentProfile.save()
            inputControlsView?.invalidate()

            // Reset flag immediately and update tracking state
            // We do this synchronously to ensure the tracking coroutine sees the correct state
            isPerformingUndoRedo = false

            // Update BOTH tracking baseline and last seen position to prevent re-tracking
            lastTrackedElement = snapshot.element
            lastTrackedX = snapshot.x
            lastTrackedY = snapshot.y
            lastTrackedScale = snapshot.scale
            lastSeenX = snapshot.x
            lastSeenY = snapshot.y
            lastSeenScale = snapshot.scale
        }
    }

    // Poll for selected element changes and track position/scale changes
    // We save the element state when the user STARTS moving it, not during the drag
    // This creates a snapshot of the position BEFORE the drag, so undo restores the pre-drag position
    LaunchedEffect(inputControlsView) {
        var isTracking = false
        var trackedElement: com.winlator.inputcontrols.ControlElement? = null
        // Note: lastSeenX/Y/Scale and lastTrackedX/Y/Scale are now state variables in outer scope
        // so they can be updated from performUndo()/performRedo() to prevent re-tracking

        try {
            while (isActive) {  // Check if coroutine is still active to prevent memory leaks
                kotlinx.coroutines.delay(100) // Check every 100ms
                inputControlsView?.let { view ->
                    val currentSelected = view.selectedElement
                    selectedElement = currentSelected

                    // Skip tracking if we're performing undo/redo to avoid creating new snapshots
                    if (isPerformingUndoRedo) {
                        return@let
                    }

                    // Track element position changes for undo
                    if (currentSelected != null) {
                        val currentX = currentSelected.x.toInt()
                        val currentY = currentSelected.y.toInt()
                        val currentScale = currentSelected.scale

                        // When element selection changes, update the baseline position
                        if (lastTrackedElement != currentSelected) {
                            // Brand new element selected - update baseline
                            lastTrackedElement = currentSelected
                            lastTrackedX = currentX
                            lastTrackedY = currentY
                            lastTrackedScale = currentScale
                            lastSeenX = currentX
                            lastSeenY = currentY
                            lastSeenScale = currentScale
                            // Reset tracking state since this is a new element
                            isTracking = false
                        } else {
                            // Same element - check if it's moving
                            val isMoving = lastSeenX != null && lastSeenY != null &&
                                (currentX != lastSeenX || currentY != lastSeenY || currentScale != lastSeenScale)

                            if (isMoving && !isTracking) {
                                // Movement just started - save snapshot with the baseline position BEFORE movement
                                // Use lastTrackedX/Y/Scale which holds the position before movement started
                                saveElementStateForUndo(currentSelected, lastTrackedX, lastTrackedY, lastTrackedScale)
                                isTracking = true
                                trackedElement = currentSelected
                            } else if (!isMoving && isTracking) {
                                // Movement stopped - update baseline to final position
                                lastTrackedX = currentX
                                lastTrackedY = currentY
                                lastTrackedScale = currentScale
                                isTracking = false
                                trackedElement = null
                            }

                            // Always update last seen position
                            lastSeenX = currentX
                            lastSeenY = currentY
                            lastSeenScale = currentScale
                        }
                    } else {
                        // No element selected - reset tracking state
                        isTracking = false
                        trackedElement = null
                        lastTrackedElement = null
                        lastSeenX = null
                        lastSeenY = null
                        lastSeenScale = null
                    }
                }
            }
        } finally {
            // Cleanup when coroutine is cancelled
        }
    }

    Dialog(
        onDismissRequest = {
            // Don't save on dismiss - only on explicit save
            undoStack.clear() // Clear temp undo log on dismiss
            redoStack.clear() // Clear temp redo log on dismiss
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Apply system UI hiding directly to the Dialog's window for full immersive mode
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                // Use WindowCompat and WindowInsetsControllerCompat for proper system UI hiding
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController?.let { controller ->
                    // Hide both status bars and navigation bars
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    // Use BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE for immersive sticky mode
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }

        // Full screen Box for canvas
        Box(modifier = Modifier.fillMaxSize()) {
            // Don't apply padding for On-Screen Controls tab to avoid black bars
            when (selectedTab) {
                0 -> {
                    // Virtual Controls Editor Tab - Full screen without any padding
                    Box(modifier = Modifier.fillMaxSize()) {
                        // AndroidView for InputControlsView
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val view = InputControlsView(ctx)
                                view.setProfile(currentProfile)
                                view.setEditMode(true)
                                // Show touchscreen controls in edit mode so elements are visible
                                view.setShowTouchscreenControls(true)
                                view.setOverlayOpacity(1.0f)  // Full opacity for clean white visibility in edit mode
                                view.invalidate()  // Force redraw with new opacity
                                inputControlsView = view
                                view
                            }
                        )

                        // Compact collapsible floating toolbar
                        if (showFloatingToolbar) {
                            Surface(
                                modifier = Modifier
                                    .offset { IntOffset(toolbarOffsetX.toInt(), toolbarOffsetY.toInt()) }
                                    .padding(8.dp)
                                    .widthIn(max = 280.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .widthIn(max = 280.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Always-visible header: Drag handle + Profile name + Save/Close + Expand button
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Drag handle
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Drag",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .pointerInput(Unit) {
                                                    detectDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        toolbarOffsetX += dragAmount.x
                                                        toolbarOffsetY += dragAmount.y
                                                    }
                                                },
                                            tint = androidx.compose.ui.graphics.Color.DarkGray
                                        )

                                        // Profile name (clickable to rename)
                                        Text(
                                            text = currentProfile.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            modifier = Modifier
                                                .weight(1f)
                                                .widthIn(max = 120.dp)
                                                .clickable { showRenameDialog = true }
                                        )

                                        // Physical Controller switch button - always visible
                                        IconButton(
                                            onClick = {
                                                android.app.AlertDialog.Builder(context)
                                                    .setTitle("Physical Controller")
                                                    .setMessage("Save before switching?")
                                                    .setPositiveButton("Save & Switch") { _, _ ->
                                                        currentProfile.save()
                                                        selectedTab = 1
                                                    }
                                                    .setNegativeButton("Switch") { _, _ ->
                                                        selectedTab = 1
                                                    }
                                                    .setNeutralButton("Cancel", null)
                                                    .show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Gamepad,
                                                "Physical Controller",
                                                modifier = Modifier.size(18.dp),
                                                tint = androidx.compose.ui.graphics.Color.DarkGray
                                            )
                                        }

                                        // Save button - always visible
                                        IconButton(
                                            onClick = {
                                                currentProfile.save()
                                                undoStack.clear() // Clear temp undo log on save
                                                redoStack.clear() // Clear temp redo log on save
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Saved",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                onSave()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Save,
                                                "Save",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Close button - always visible
                                        IconButton(
                                            onClick = {
                                                undoStack.clear() // Clear temp undo log on cancel/back
                                                redoStack.clear() // Clear temp redo log on cancel/back
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Close",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        // Expand/Collapse button
                                        IconButton(
                                            onClick = { isToolbarExpanded = !isToolbarExpanded },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (isToolbarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isToolbarExpanded) "Collapse" else "Expand",
                                                modifier = Modifier.size(18.dp),
                                                tint = androidx.compose.ui.graphics.Color.DarkGray
                                            )
                                        }
                                    }

                                    // Expanded state: Show all buttons
                                    if (isToolbarExpanded) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
                                        )

                                        // Primary actions row: Edit, Add, Delete, Settings, Undo
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Edit
                                            IconButton(
                                                onClick = {
                                                    if (selectedElement != null) {
                                                        showElementEditor = selectedElement
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Select an element first",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                enabled = selectedElement != null,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    "Edit",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (selectedElement != null)
                                                        androidx.compose.ui.graphics.Color.DarkGray
                                                    else
                                                        androidx.compose.ui.graphics.Color.LightGray
                                                )
                                            }

                                            // Add
                                            IconButton(
                                                onClick = {
                                                    inputControlsView?.let { view ->
                                                        showAddElementDialog(context, view)
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    "Add",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = androidx.compose.ui.graphics.Color.DarkGray
                                                )
                                            }

                                            // Delete
                                            IconButton(
                                                onClick = {
                                                    if (selectedElement != null) {
                                                        android.app.AlertDialog.Builder(context)
                                                            .setTitle("Delete Element")
                                                            .setMessage("Delete this element?")
                                                            .setPositiveButton("Delete") { _, _ ->
                                                                currentProfile.removeElement(selectedElement!!)
                                                                currentProfile.save()
                                                                inputControlsView?.invalidate()
                                                            }
                                                            .setNegativeButton("Cancel", null)
                                                            .show()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Select an element first",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    "Delete",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = androidx.compose.ui.graphics.Color.DarkGray
                                                )
                                            }

                                            // Settings
                                            IconButton(
                                                onClick = { showSensitivityDialog = true },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Settings,
                                                    "Settings",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = androidx.compose.ui.graphics.Color.DarkGray
                                                )
                                            }

                                            // Undo
                                            IconButton(
                                                onClick = {
                                                    performUndo()
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Undo",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                enabled = undoStack.isNotEmpty(),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Undo,
                                                    "Undo",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (undoStack.isNotEmpty())
                                                        androidx.compose.ui.graphics.Color.DarkGray
                                                    else
                                                        androidx.compose.ui.graphics.Color.LightGray
                                                )
                                            }

                                            // Redo
                                            IconButton(
                                                onClick = {
                                                    performRedo()
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Redo",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                enabled = redoStack.isNotEmpty(),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Redo,
                                                    "Redo",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (redoStack.isNotEmpty())
                                                        androidx.compose.ui.graphics.Color.DarkGray
                                                    else
                                                        androidx.compose.ui.graphics.Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Physical Controllers Configuration Tab
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PhysicalControllerConfigSection(
                            profile = currentProfile,
                            onSwitchToOnScreen = {
                                // Show confirmation dialog before switching
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Switch to On-Screen Controls")
                                    .setMessage("Do you want to save changes before switching to on-screen controls editor?")
                                    .setPositiveButton("Save & Switch") { _, _ ->
                                        currentProfile.save()
                                        selectedTab = 0  // Switch to on-screen controls tab
                                    }
                                    .setNegativeButton("Switch Without Saving") { _, _ ->
                                        selectedTab = 0
                                    }
                                    .setNeutralButton("Cancel", null)
                                    .show()
                            },
                            onSave = {
                                currentProfile.save()
                                undoStack.clear() // Clear temp undo log on save
                                redoStack.clear() // Clear temp redo log on save
                                android.widget.Toast.makeText(
                                    context,
                                    "Profile saved",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onSave()
                            },
                            onDismiss = onDismiss,
                            onProfileUpdated = {
                                // Profile was updated, refresh the view
                                inputControlsView?.let { view ->
                                    val refreshedProfile = InputControlsManager(context).getProfile(currentProfile.id)
                                    if (refreshedProfile != null) {
                                        view.setProfile(refreshedProfile)
                                    }
                                }
                            },
                            onRenameProfile = {
                                showRenameDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Show Element Editor Dialog
    showElementEditor?.let { element ->
        inputControlsView?.let { view ->
            ElementEditorDialog(
                element = element,
                view = view,
                onDismiss = { showElementEditor = null },
                onSave = {
                    showElementEditor = null
                    view.invalidate()
                }
            )
        }
    }

    // Show Sensitivity Settings Dialog
    if (showSensitivityDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSensitivityDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "On-Screen Controls Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Configure dead zones and sensitivity for on-screen controls. These settings only affect virtual touch controls, not physical controllers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // Sensitivity settings component
                    app.gamenative.ui.component.settings.SensitivitySettingsSection(
                        profile = currentProfile,
                        isPhysicalController = false,
                        onSettingsChanged = {
                            inputControlsView?.invalidate()
                        }
                    )

                    // Touchpad gesture settings
                    app.gamenative.ui.component.settings.TouchpadGestureSettings(
                        profile = currentProfile,
                        onSettingsChanged = {
                            inputControlsView?.invalidate()
                        }
                    )

                    // Mouse and touch behavior settings
                    app.gamenative.ui.component.settings.MouseTouchBehaviorSettings(
                        profile = currentProfile,
                        onSettingsChanged = {
                            inputControlsView?.invalidate()
                        }
                    )

                    // Close button
                    Button(
                        onClick = { showSensitivityDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Show Rename Profile Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentProfile.name) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showRenameDialog = false
                errorMessage = null
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Rename Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Name input field
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            errorMessage = null
                        },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showRenameDialog = false
                            errorMessage = null
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Validate the name
                                val validation = InputControlsManager.validateProfileName(newName)
                                if (validation != null) {
                                    errorMessage = validation
                                } else {
                                    // Check if name already exists (excluding current profile)
                                    val manager = InputControlsManager(context)
                                    val existingProfile = manager.profiles.find {
                                        it.name == newName && it.id != currentProfile.id
                                    }
                                    if (existingProfile != null) {
                                        errorMessage = "A profile with this name already exists"
                                    } else {
                                        // Update the profile name
                                        currentProfile.name = newName
                                        currentProfile.save()
                                        showRenameDialog = false
                                        errorMessage = null
                                        Toast.makeText(
                                            context,
                                            "Profile renamed to '$newName'",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text("Rename")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper function to show dialog for adding a control element
 */
