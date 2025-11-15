package app.gamenative.ui.component.dialog

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager

/**
 * Dialog for creating a new profile from a template
 *
 * Features:
 * - Shows all template profiles for selection
 * - Input field for new profile name
 * - Checkbox to lock to current game (checked by default when in game context)
 * - Clones the selected template with the specified name and lock status
 */
@Composable
fun CreateFromTemplateDialog(
    context: Context,
    container: Container,
    onProfileCreated: (ControlsProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val inputControlsManager = remember { InputControlsManager(context) }

    // Show templates by default, but allow viewing all profiles
    var showAllProfiles by remember { mutableStateOf(false) }

    val availableProfiles = remember(showAllProfiles) {
        if (showAllProfiles) {
            inputControlsManager.getProfiles(false) // All profiles
        } else {
            inputControlsManager.templates // Only templates
        }
    }

    var selectedTemplate by remember { mutableStateOf<ControlsProfile?>(null) }
    var profileName by remember { mutableStateOf("") }
    var lockToGame by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }

    // Auto-generate profile name when template is selected
    LaunchedEffect(selectedTemplate, container) {
        selectedTemplate?.let { template ->
            // Strip "Template" from the template name
            val templateNameWithoutTemplate = template.name.replace(Regex("\\s*[Tt]emplate\\s*"), " ").trim()
            profileName = InputControlsManager.sanitizeProfileName("${container.name} - $templateNameWithoutTemplate")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Create from Template",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "For: ${container.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Profile name input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCreating
                )

                // Lock to game checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Lock to ${container.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Only show this profile for this game",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Checkbox(
                        checked = lockToGame,
                        onCheckedChange = { lockToGame = it },
                        enabled = !isCreating
                    )
                }

                HorizontalDivider()

                // Template selection header with toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAllProfiles) "Select Profile" else "Select Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show all",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Switch(
                            checked = showAllProfiles,
                            onCheckedChange = {
                                showAllProfiles = it
                                selectedTemplate = null // Reset selection when toggling
                            },
                            enabled = !isCreating
                        )
                    }
                }

                // Profile list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (availableProfiles.isEmpty()) {
                        item {
                            Text(
                                text = if (showAllProfiles) "No profiles available" else "No templates available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(availableProfiles) { template ->
                            TemplateCard(
                                template = template,
                                isSelected = template == selectedTemplate,
                                onClick = { selectedTemplate = template },
                                enabled = !isCreating
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            val template = selectedTemplate
                            val name = profileName.trim()

                            // Validation
                            val validationError = InputControlsManager.validateProfileName(name)
                            if (validationError != null) {
                                Toast.makeText(context, validationError, Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (template == null) {
                                Toast.makeText(context, "Please select a template", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isCreating = true

                            try {
                                // Clone the template with the specified name and lock status
                                val lockedToContainer = if (lockToGame) {
                                    container.id.toString()
                                } else {
                                    null
                                }

                                val newProfile = inputControlsManager.cloneProfile(
                                    template,
                                    name,
                                    lockedToContainer
                                )

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
                        enabled = !isCreating && selectedTemplate != null && profileName.trim().isNotEmpty()
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
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ControlsProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                // Show type/status
                if (template.isTemplate) {
                    Text(
                        text = "Template",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (template.isLockedToGame) {
                    Text(
                        text = "Game-locked profile",
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
