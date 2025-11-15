package app.gamenative.ui.component.dialog

import android.content.Context
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager

/**
 * Unified dialog for creating new profiles
 *
 * Features:
 * - Three tabs: Templates, Global Profiles, Game-Locked Profiles
 * - Search functionality for each tab
 * - Options to create blank or copy from selected profile
 * - Input field for new profile name
 * - Checkbox to lock to current game
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedProfileCreationDialog(
    context: Context,
    container: Container?,
    onProfileCreated: (ControlsProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val inputControlsManager = remember { InputControlsManager(context) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Tab selection: 0 = Templates, 1 = Global Profiles, 2 = Game-Locked Profiles
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Get profiles based on selected tab
    val allProfiles = remember(selectedTab) {
        when (selectedTab) {
            0 -> inputControlsManager.templates // Templates
            1 -> inputControlsManager.globalProfiles.filter { !it.isTemplate } // Global profiles (non-templates)
            2 -> inputControlsManager.getProfiles(false).filter { it.isLockedToGame } // Game-locked profiles
            else -> emptyList()
        }
    }

    // Filter profiles by search query
    val profiles = remember(allProfiles, searchQuery) {
        if (searchQuery.isBlank()) {
            allProfiles
        } else {
            allProfiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    var selectedProfile by remember { mutableStateOf<ControlsProfile?>(null) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var lockToGame by rememberSaveable { mutableStateOf(container != null) } // Default to locked if container provided
    var isCreating by remember { mutableStateOf(false) }
    var createMode by rememberSaveable { mutableStateOf<CreateMode?>(null) } // null = not chosen yet, BLANK, COPY, or IMPORT
    var showFileBrowser by remember { mutableStateOf(false) }

    // Handle file selection from in-app browser
    fun handleFileImport(file: java.io.File) {
        try {
            // Read file content directly
            val content = file.readText()

            // Parse and import
            val jsonData = org.json.JSONObject(content)
            inputControlsManager.getProfiles() // Ensure profiles are loaded
            val importedProfile = inputControlsManager.importProfile(jsonData)

            if (importedProfile != null) {
                // Apply game lock if specified
                if (lockToGame && container != null) {
                    importedProfile.setLockedToContainer(container.id.toString())
                    importedProfile.save()
                }

                Toast.makeText(
                    context,
                    "Profile '${importedProfile.name}' imported successfully",
                    Toast.LENGTH_LONG
                ).show()

                // Notify parent to refresh the list and close dialog
                onProfileCreated(importedProfile)
                onDismiss()
            } else {
                Toast.makeText(
                    context,
                    "Failed to import profile: Invalid format",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Error importing profile: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    // Reset selection when changing tabs
    LaunchedEffect(selectedTab) {
        selectedProfile = null
        searchQuery = ""
    }

    // Auto-generate profile name when source profile is selected (for copy mode)
    LaunchedEffect(selectedProfile, createMode, container) {
        if (createMode == CreateMode.COPY && selectedProfile != null) {
            val source = selectedProfile!!
            // Strip "Template" from the source name if it's a template
            val sourceNameWithoutTemplate = source.name.replace(Regex("\\s*[Tt]emplate\\s*"), " ").trim()

            val baseName = if (container != null) {
                if (source.isTemplate) {
                    // Use template name without "Template" word
                    "${container.name} - $sourceNameWithoutTemplate"
                } else {
                    "${container.name} Profile"
                }
            } else {
                // No container - creating global profile
                if (source.isTemplate) {
                    // Use template name without "Template" word
                    sourceNameWithoutTemplate.ifEmpty { "New Profile" }
                } else {
                    "New Profile"
                }
            }

            // Find next available number if name exists
            val allExistingProfiles = inputControlsManager.getProfiles(false)
            var finalName = baseName
            var counter = 2

            while (allExistingProfiles.any { it.name == finalName }) {
                finalName = "$baseName $counter"
                counter++
            }

            profileName = finalName
        } else if (createMode == CreateMode.BLANK) {
            // Auto-generate name for blank profile
            val allExistingProfiles = inputControlsManager.getProfiles(false)
            val baseName = if (container != null) {
                "${container.name} Profile"
            } else {
                "New Profile"
            }
            var finalName = baseName
            var counter = 2

            while (allExistingProfiles.any { it.name == finalName }) {
                finalName = "$baseName $counter"
                counter++
            }

            profileName = finalName
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(if (isLandscape) 0.95f else 0.9f), // Use more height in landscape
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header - more compact in landscape
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isLandscape) 8.dp else 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                        )
                        Column {
                            Text(
                                text = "Create New Profile",
                                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (container != null) {
                                Text(
                                    text = "For: ${container.name}",
                                    style = if (isLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                HorizontalDivider()

                // Show mode selection dialog first if mode not chosen
                if (createMode == null) {
                    // Mode selection screen - more compact in landscape
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isLandscape) 12.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isLandscape) {
                            Spacer(modifier = Modifier.weight(0.5f))
                        }

                        Text(
                            text = "How would you like to create the profile?",
                            style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 16.dp))

                        // Create Blank option - more compact in landscape
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { createMode = CreateMode.BLANK },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isLandscape) 12.dp else 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLandscape) 32.dp else 48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Create Blank Profile",
                                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Start with an empty profile to customize from scratch",
                                        style = if (isLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Copy from Existing option - more compact in landscape
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { createMode = CreateMode.COPY },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isLandscape) 12.dp else 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FileCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLandscape) 32.dp else 48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Copy from Existing",
                                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Copy from templates, global profiles, or game-locked profiles",
                                        style = if (isLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Import Profile option - more compact in landscape
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFileBrowser = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isLandscape) 12.dp else 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLandscape) 32.dp else 48.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Import Profile",
                                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Import a profile from a .icp file",
                                        style = if (isLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!isLandscape) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    // Mode has been chosen, show the appropriate UI

                // Profile name and settings section - more compact in landscape
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isLandscape) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                ) {
                    // Profile name input
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("New Profile Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isCreating
                    )

                    // Lock to game checkbox (only show if container is provided)
                    if (container != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Lock to ${container.name}",
                                        style = if (isLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!isLandscape) {
                                        Text(
                                            text = "Only show this profile for this game",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Checkbox(
                                checked = lockToGame,
                                onCheckedChange = { lockToGame = it },
                                enabled = !isCreating
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Show profile selection UI only in COPY mode
                if (createMode == CreateMode.COPY) {
                    // Tabs for profile categories
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Templates", style = MaterialTheme.typography.labelMedium) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Global", style = MaterialTheme.typography.labelMedium) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Game-Locked", style = MaterialTheme.typography.labelMedium) }
                        )
                    }

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search profiles...") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        enabled = !isCreating
                    )

                    // Profile list
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (profiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> if (searchQuery.isBlank()) "No templates available" else "No templates found"
                                        1 -> if (searchQuery.isBlank()) "No global profiles available" else "No global profiles found"
                                        2 -> if (searchQuery.isBlank()) "No game-locked profiles available" else "No game-locked profiles found"
                                        else -> "No profiles available"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            profiles.forEach { profile ->
                                ProfileCard(
                                    profile = profile,
                                    isSelected = profile == selectedProfile,
                                    container = container,
                                    onClick = { selectedProfile = profile },
                                    enabled = !isCreating,
                                    isLandscape = isLandscape
                                )
                            }
                        }
                    }
                } else {
                    // Blank mode - just show some info text
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Create a blank profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Start with an empty profile to customize from scratch",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons (only show when mode is selected) - more compact in landscape
                if (createMode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isLandscape) 8.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isCreating
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val name = profileName.trim()

                            // Validation
                            if (name.isEmpty()) {
                                Toast.makeText(context, "Please enter a profile name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (createMode == CreateMode.COPY && selectedProfile == null) {
                                Toast.makeText(context, "Please select a profile to copy", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isCreating = true

                            try {
                                val lockedToContainer = if (lockToGame && container != null) {
                                    container.id.toString()
                                } else {
                                    null
                                }

                                val newProfile = if (createMode == CreateMode.BLANK) {
                                    // Create blank profile
                                    val profile = inputControlsManager.createProfile(name)
                                    profile.setLockedToContainer(lockedToContainer)
                                    profile.save()
                                    profile
                                } else {
                                    // Clone selected profile
                                    inputControlsManager.cloneProfile(
                                        selectedProfile!!,
                                        name,
                                        lockedToContainer
                                    )
                                }

                                Toast.makeText(
                                    context,
                                    "Profile '$name' created successfully",
                                    Toast.LENGTH_SHORT
                                ).show()

                                onProfileCreated(newProfile)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Failed to create profile: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                isCreating = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isCreating && profileName.trim().isNotEmpty() &&
                                (createMode == CreateMode.BLANK || selectedProfile != null)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Create")
                        }
                    }
                }
                } // End createMode != null
                } // End createMode == null else
            }
        }
    }

    // Show in-app file browser dialog
    if (showFileBrowser) {
        InAppFileBrowserDialog(
            context = context,
            onFileSelected = { file ->
                showFileBrowser = false
                handleFileImport(file)
            },
            onDismiss = {
                showFileBrowser = false
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ControlsProfile,
    isSelected: Boolean,
    container: Container?,
    onClick: () -> Unit,
    enabled: Boolean,
    isLandscape: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLandscape) 10.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: Profile name with icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon based on profile type
                    if (profile.isTemplate) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Template",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                        )
                    } else if (profile.isLockedToGame) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Game-locked profile",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = "Global profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                        )
                    }

                    Text(
                        text = profile.name,
                        style = if (isLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Row 2: ID and element count
                Text(
                    text = "ID: ${profile.id} • ${profile.elements.size} elements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Row 3: Type/status
                if (profile.isTemplate) {
                    Text(
                        text = "Template",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (profile.isLockedToGame) {
                    val containerName = getContainerDisplayName(profile.lockedToContainer, container)
                    Text(
                        text = "Locked to: $containerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = "Global profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Helper function to get display name for a container
 */
private fun getContainerDisplayName(lockedToContainer: String?, currentContainer: Container?): String {
    if (lockedToContainer == null) return "Unknown"

    // If this is the current container, show "This Game"
    if (currentContainer != null && lockedToContainer == currentContainer.id.toString()) {
        return "This Game (${currentContainer.name})"
    }

    // Otherwise, try to extract a readable name from the container ID
    return "Game #$lockedToContainer"
}

/**
 * Mode for creating profiles
 */
private enum class CreateMode {
    BLANK,  // Create empty profile
    COPY,   // Copy from existing profile
    IMPORT  // Import from file
}
