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

                                        // Profile name
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
}

/**
 * Helper function to show dialog for adding a control element
 */
private fun showAddElementDialog(context: Context, view: InputControlsView) {
    val profile = view.profile ?: return

    val items = arrayOf(
        "D-Pad",
        "Button (Circle)",
        "Button (Rectangle)",
        "Button (Round Rectangle)",
        "Analog Stick",
        "Trackpad"
    )

    android.app.AlertDialog.Builder(context)
        .setTitle("Add Control Element")
        .setItems(items) { _, which ->
            val type = when (which) {
                0 -> com.winlator.inputcontrols.ControlElement.Type.D_PAD
                1, 2, 3 -> com.winlator.inputcontrols.ControlElement.Type.BUTTON
                4 -> com.winlator.inputcontrols.ControlElement.Type.STICK
                5 -> com.winlator.inputcontrols.ControlElement.Type.TRACKPAD
                else -> com.winlator.inputcontrols.ControlElement.Type.BUTTON
            }

            val shape = when (which) {
                0, 1, 4, 5 -> com.winlator.inputcontrols.ControlElement.Shape.CIRCLE
                2 -> com.winlator.inputcontrols.ControlElement.Shape.RECT
                3 -> com.winlator.inputcontrols.ControlElement.Shape.ROUND_RECT
                else -> com.winlator.inputcontrols.ControlElement.Shape.CIRCLE
            }

            // Create new element at center of screen using actual pixel coordinates
            val element = com.winlator.inputcontrols.ControlElement(view)
            element.setType(type)
            element.setShape(shape)

            // Use actual screen pixel coordinates (not Short.MAX_VALUE)
            element.setX(view.width / 2)
            element.setY(view.height / 2)
            element.setScale(1.0f)

            // Set a sensible default binding for buttons (Space key)
            // Other types (D-Pad, Stick, Trackpad) already have defaults from reset()
            if (type == com.winlator.inputcontrols.ControlElement.Type.BUTTON) {
                element.setBindingAt(0, com.winlator.inputcontrols.Binding.KEY_SPACE)
            }

            profile.addElement(element)
            profile.save()  // Save the profile so the new element is persisted
            view.invalidate()
        }
        .show()
}

/**
 * Helper function to show dialog for editing a control element's properties
 */
private fun showEditElementDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    val options = arrayOf(
        "Edit Bindings",
        "Edit Text/Label",
        "Change Type",
        "Change Shape",
        "Adjust Size/Scale",
        "View Properties"
    )

    android.app.AlertDialog.Builder(context)
        .setTitle("Edit Element")
        .setItems(options) { _, which ->
            when (which) {
                0 -> showBindingsEditorDialog(context, element, view)
                1 -> showEditTextDialog(context, element, view)
                2 -> showChangeTypeDialog(context, element, view)
                3 -> showChangeShapeDialog(context, element, view)
                4 -> showAdjustScaleDialog(context, element, view)
                5 -> showViewPropertiesDialog(context, element)
            }
        }
        .setNegativeButton("Close", null)
        .show()
}

/**
 * Show detailed properties view
 */
private fun showViewPropertiesDialog(context: Context, element: com.winlator.inputcontrols.ControlElement) {
    val bindings = mutableListOf<String>()
    for (i in 0 until element.bindingCount) {
        val binding = element.getBindingAt(i)
        if (binding != null && binding != com.winlator.inputcontrols.Binding.NONE) {
            bindings.add("  Slot ${i + 1}: ${binding}")
        }
    }

    val message = buildString {
        append("Type: ${element.type}\n")
        append("Shape: ${element.shape}\n")
        append("Text: ${element.text.ifEmpty { "(none)" }}\n")
        append("Scale: ${"%.2f".format(element.scale)}\n")
        append("Position: (${element.x}, ${element.y})\n")
        append("\nBindings (${element.bindingCount} slots):\n")
        if (bindings.isEmpty()) {
            append("  (no bindings configured)")
        } else {
            bindings.forEach { append("$it\n") }
        }
    }

    android.app.AlertDialog.Builder(context)
        .setTitle("Element Properties")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

/**
 * Change element type dialog
 */
private fun showChangeTypeDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    val types = com.winlator.inputcontrols.ControlElement.Type.values()
    val typeNames = types.map { it.name.replace("_", " ") }.toTypedArray()
    val currentIndex = types.indexOf(element.type)

    android.app.AlertDialog.Builder(context)
        .setTitle("Change Element Type")
        .setSingleChoiceItems(typeNames, currentIndex) { dialog, which ->
            element.setType(types[which])
            view.profile?.save()
            view.invalidate()
            dialog.dismiss()

            android.widget.Toast.makeText(
                context,
                "Type changed to: ${types[which].name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Change element shape dialog
 */
private fun showChangeShapeDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    val allShapes = com.winlator.inputcontrols.ControlElement.Shape.values()

    // Filter available shapes based on element type
    // STICK, TRACKPAD, RANGE_BUTTON have fixed rendering shapes
    val availableShapes = when (element.type) {
        com.winlator.inputcontrols.ControlElement.Type.STICK -> {
            // Stick is always rendered as CIRCLE
            listOf(com.winlator.inputcontrols.ControlElement.Shape.CIRCLE)
        }
        com.winlator.inputcontrols.ControlElement.Type.TRACKPAD,
        com.winlator.inputcontrols.ControlElement.Type.RANGE_BUTTON -> {
            // Trackpad and Range Button are always rendered as ROUND_RECT
            listOf(com.winlator.inputcontrols.ControlElement.Shape.ROUND_RECT)
        }
        com.winlator.inputcontrols.ControlElement.Type.D_PAD -> {
            // D-Pad uses custom cross-shaped path, shape doesn't affect rendering
            // Allow all shapes but warn user
            allShapes.toList()
        }
        com.winlator.inputcontrols.ControlElement.Type.BUTTON -> {
            // Buttons fully support all shapes
            allShapes.toList()
        }
        else -> allShapes.toList()
    }

    // Check if element type has restricted shapes
    if (availableShapes.size == 1) {
        android.widget.Toast.makeText(
            context,
            "${element.type.name} elements can only use ${availableShapes[0].name} shape",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }

    val shapeNames = availableShapes.map { it.name.replace("_", " ") }.toTypedArray()
    val currentIndex = availableShapes.indexOf(element.shape).let { if (it == -1) 0 else it }

    val dialogBuilder = android.app.AlertDialog.Builder(context)
        .setTitle("Change Element Shape")
        .setSingleChoiceItems(shapeNames, currentIndex) { dialog, which ->
            element.setShape(availableShapes[which])
            view.profile?.save()
            view.invalidate()
            dialog.dismiss()

            android.widget.Toast.makeText(
                context,
                "Shape changed to: ${availableShapes[which].name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        .setNegativeButton("Cancel", null)

    // Add warning message for D-Pad
    if (element.type == com.winlator.inputcontrols.ControlElement.Type.D_PAD) {
        dialogBuilder.setMessage("Note: D-Pad uses a fixed cross-shaped design. Changing the shape property won't affect its visual appearance.")
    }

    dialogBuilder.show()
}

/**
 * Adjust element scale/size dialog with live preview
 */
private fun showAdjustScaleDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    val scaleOptions = arrayOf(
        "50% (0.5x)",
        "75% (0.75x)",
        "100% (1.0x) - Default",
        "125% (1.25x)",
        "150% (1.5x)",
        "175% (1.75x)",
        "200% (2.0x)",
        "250% (2.5x)",
        "Custom..."
    )

    val scaleValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)

    // Store original scale to restore on cancel
    val originalScale = element.scale

    // Find closest match to current scale
    var selectedIndex = scaleValues.indexOfFirst { kotlin.math.abs(it - originalScale) < 0.01f }
    if (selectedIndex < 0) selectedIndex = 2 // Default to 1.0x

    val dialog = android.app.AlertDialog.Builder(context)
        .setTitle("Adjust Size (Current: ${"%.2f".format(originalScale)}x)")
        .setSingleChoiceItems(scaleOptions, selectedIndex) { dlg, which ->
            if (which == scaleOptions.size - 1) {
                // Custom scale - restore original first, then show custom dialog
                element.setScale(originalScale)
                view.invalidate()
                dlg.dismiss()
                showCustomScaleDialog(context, element, view)
            } else {
                // Apply new scale and save
                element.setScale(scaleValues[which])
                view.profile?.save()
                view.invalidate()
                dlg.dismiss()

                android.widget.Toast.makeText(
                    context,
                    "Scale changed to: ${scaleValues[which]}x",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        .setNegativeButton("Cancel") { dlg, _ ->
            // Restore original scale on cancel
            element.setScale(originalScale)
            view.invalidate()
            dlg.dismiss()
        }
        .setOnCancelListener {
            // Restore original scale if dialog dismissed
            element.setScale(originalScale)
            view.invalidate()
        }
        .create()

    // Set up live preview - update scale as user selects different options
    dialog.listView?.setOnItemClickListener { _, _, position, _ ->
        if (position < scaleValues.size) {
            // Update scale in real-time for preview
            element.setScale(scaleValues[position])
            view.invalidate()
        }
    }

    dialog.show()
}

/**
 * Custom scale input dialog with live preview
 */
private fun showCustomScaleDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    // Store original scale to restore on cancel
    val originalScale = element.scale

    val input = android.widget.EditText(context)
    input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    input.setText(originalScale.toString())
    input.selectAll()

    // Add text watcher for live preview
    input.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            try {
                val scale = s.toString().toFloatOrNull()
                if (scale != null && scale in 0.1f..5.0f) {
                    // Update scale in real-time for preview
                    element.setScale(scale)
                    view.invalidate()
                }
            } catch (e: Exception) {
                // Ignore invalid input during typing
            }
        }
    })

    android.app.AlertDialog.Builder(context)
        .setTitle("Enter Custom Scale")
        .setMessage("Enter a scale value (0.1 to 5.0)\nChanges preview in real-time as you type.")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
            try {
                val scale = input.text.toString().toFloat()
                if (scale in 0.1f..5.0f) {
                    element.setScale(scale)
                    view.profile?.save()
                    view.invalidate()

                    android.widget.Toast.makeText(
                        context,
                        "Scale changed to: ${scale}x",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Restore original scale
                    element.setScale(originalScale)
                    view.invalidate()

                    android.widget.Toast.makeText(
                        context,
                        "Scale must be between 0.1 and 5.0",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: NumberFormatException) {
                // Restore original scale
                element.setScale(originalScale)
                view.invalidate()

                android.widget.Toast.makeText(
                    context,
                    "Invalid scale value",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        .setNegativeButton("Cancel") { _, _ ->
            // Restore original scale on cancel
            element.setScale(originalScale)
            view.invalidate()
        }
        .setOnCancelListener {
            // Restore original scale if dialog dismissed
            element.setScale(originalScale)
            view.invalidate()
        }
        .show()
}

/**
 * Show dialog to edit element text/label
 */
private fun showEditTextDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    val input = android.widget.EditText(context)
    input.setText(element.text)

    android.app.AlertDialog.Builder(context)
        .setTitle("Edit Element Text")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
            element.setText(input.text.toString())
            view.profile?.save()
            view.invalidate()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Show dialog to view and edit element bindings
 */
private fun showBindingsEditorDialog(context: Context, element: com.winlator.inputcontrols.ControlElement, view: InputControlsView) {
    // Special handling for RANGE_BUTTON - bindings are auto-generated
    if (element.type == com.winlator.inputcontrols.ControlElement.Type.RANGE_BUTTON) {
        android.widget.Toast.makeText(
            context,
            "Range Button bindings are auto-generated from the Range setting (A-Z, 0-9, F1-F12, etc.)",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return
    }

    // Determine which slots to show and their labels based on element type
    data class SlotInfo(val index: Int, val label: String, val description: String)

    val slotInfo = when (element.type) {
        com.winlator.inputcontrols.ControlElement.Type.BUTTON -> {
            // Buttons only use slots 0 and 1 - BOTH are pressed simultaneously
            listOf(
                SlotInfo(0, "Primary Action", "Main key/button (always pressed)"),
                SlotInfo(1, "Secondary Action", "Optional - pressed simultaneously with primary (e.g., for Shift+A, Ctrl+Click)")
            )
        }
        com.winlator.inputcontrols.ControlElement.Type.D_PAD -> {
            // D-Pad uses all 4 directional slots
            listOf(
                SlotInfo(0, "Up", "Triggered when pressing up on D-Pad"),
                SlotInfo(1, "Right", "Triggered when pressing right on D-Pad"),
                SlotInfo(2, "Down", "Triggered when pressing down on D-Pad"),
                SlotInfo(3, "Left", "Triggered when pressing left on D-Pad")
            )
        }
        com.winlator.inputcontrols.ControlElement.Type.STICK -> {
            // Analog stick uses all 4 directional slots
            listOf(
                SlotInfo(0, "Up", "Analog stick pushed up"),
                SlotInfo(1, "Right", "Analog stick pushed right"),
                SlotInfo(2, "Down", "Analog stick pushed down"),
                SlotInfo(3, "Left", "Analog stick pushed left")
            )
        }
        com.winlator.inputcontrols.ControlElement.Type.TRACKPAD -> {
            // Trackpad uses all 4 directional slots for mouse movement
            listOf(
                SlotInfo(0, "Up", "Swipe up for mouse movement"),
                SlotInfo(1, "Right", "Swipe right for mouse movement"),
                SlotInfo(2, "Down", "Swipe down for mouse movement"),
                SlotInfo(3, "Left", "Swipe left for mouse movement")
            )
        }
        else -> {
            // Fallback: show all available slots
            (0 until element.bindingCount).map {
                SlotInfo(it, "Slot ${it + 1}", "Custom binding slot")
            }
        }
    }

    // Add header note for buttons
    val headerNote = if (element.type == com.winlator.inputcontrols.ControlElement.Type.BUTTON) {
        "Note: Both slots are pressed simultaneously when button is touched. Use secondary slot for modifier keys (Shift, Ctrl, Alt) or dual-key combos.\n\n"
    } else ""

    // Build slot descriptions with current bindings
    val bindingSlots = slotInfo.map { slot ->
        val binding = element.getBindingAt(slot.index)
        val bindingName = binding?.toString() ?: "NONE"
        "${slot.label}: $bindingName\n  ${slot.description}"
    }.toTypedArray()

    android.app.AlertDialog.Builder(context)
        .setTitle("Edit Bindings - ${element.type.name}")
        .setMessage(headerNote)
        .setItems(bindingSlots) { _, which ->
            val slot = slotInfo[which]
            showBindingSelector(context, element, slot.index, slot.label, view)
        }
        .setNegativeButton("Close", null)
        .show()
}

/**
 * Show binding selector for a specific slot
 */
private fun showBindingSelector(context: Context, element: com.winlator.inputcontrols.ControlElement, slotIndex: Int, slotLabel: String, view: InputControlsView) {
    val currentBinding = element.getBindingAt(slotIndex)

    // Get all available bindings
    val allBindings = com.winlator.inputcontrols.Binding.values()
    val bindingNames = allBindings.map { it.toString() }.toTypedArray()

    // Find currently selected index
    val selectedIndex = allBindings.indexOf(currentBinding).coerceAtLeast(0)

    android.app.AlertDialog.Builder(context)
        .setTitle("Select Binding: $slotLabel")
        .setSingleChoiceItems(bindingNames, selectedIndex) { dialog, which ->
            element.setBindingAt(slotIndex, allBindings[which])
            view.profile?.save()
            view.invalidate()
            dialog.dismiss()

            // Show toast
            android.widget.Toast.makeText(
                context,
                "Binding updated to: ${allBindings[which]}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Physical Controller Configuration Section
 * Shows controller bindings for the profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhysicalControllerConfigSection(
    profile: ControlsProfile,
    onSwitchToOnScreen: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onProfileUpdated: () -> Unit
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
                    // Force reload controllers from disk to refresh UI
                    controllers = profile.loadControllers()
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
                        // Force reload controllers from disk to refresh UI
                        controllers = profile.loadControllers()
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
private fun ControllerBindingsSection(
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
private fun BindingListItem(
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
private fun DetectedControllersCard() {
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

/**
 * Quick preset buttons for common binding patterns
 */
@Composable
private fun BindingPresetsSection(
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
private fun ResetToDefaultDialog(
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
private enum class PresetType {
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
private fun applyPreset(
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

    // Apply each mapping
    mappings.forEach { (keyCode, binding) ->
        // Remove existing binding for this keyCode
        controller.controllerBindings.removeIf { it.keyCodeForAxis == keyCode }

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
private fun PresetButton(
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
