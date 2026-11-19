package com.eyalm.adns.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.ui.components.rememberAccountEpoch
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.api.NextDnsProfile
import com.eyalm.adns.domain.nextdns.ProfileCapabilities
import com.eyalm.adns.ui.components.dialogs.DestructiveConfirmationDialog
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.viewmodel.nextdns.ProfileAction
import com.eyalm.adns.viewmodel.nextdns.ProfileActionDialog
import com.eyalm.adns.viewmodel.nextdns.ProfileActionsEffect
import com.eyalm.adns.viewmodel.nextdns.ProfileActionsViewModel
import com.eyalm.adns.viewmodel.nextdns.ProfileNameError

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogsActionsSection(
    profile: NextDnsProfile,
    capabilities: ProfileCapabilities,
    onLogsCleared: () -> Unit,
) {
    val epoch = rememberAccountEpoch()
    val viewModel: ProfileActionsViewModel =
        viewModel(key = "log-actions-${epoch.sequence}-${profile.id}")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { destination ->
        if (destination != null) viewModel.exportLogs(profile.id, destination)
    }

    LaunchedEffect(capabilities) {
        viewModel.setCapabilities(capabilities)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileActionsEffect.Message ->
                    Toast.makeText(context, effect.value, Toast.LENGTH_SHORT).show()

                ProfileActionsEffect.LogsCleared -> onLogsCleared()
                is ProfileActionsEffect.ProfileRemoved,
                ProfileActionsEffect.ProfileDuplicated,
                ProfileActionsEffect.ProfileRenamed -> Unit
            }
        }
    }

    Column {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val buttonCount = if (capabilities.canEditSettings) 2 else 1
            val cornerRadius = 12.dp

            val downloadShape = if (buttonCount > 1) {
                RoundedCornerShape(bottomStart = cornerRadius)
            } else {
                RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
            }
            ToggleButton(
                checked = false,
                onCheckedChange = { createDocument.launch("${profile.id}-logs.csv") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shapes = ToggleButtonDefaults.shapes(
                    shape = downloadShape,
                    pressedShape = downloadShape,
                    checkedShape = downloadShape
                ),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (state.inFlight == ProfileAction.DownloadLogs) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                }
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(
                    text = Locales.getString("settings", "logs", "download", "button"),
                    maxLines = 1
                )
            }


            if (capabilities.canEditSettings) {
                val clearShape = RoundedCornerShape(bottomEnd = cornerRadius)
                ToggleButton(
                    checked = false,
                    onCheckedChange = { viewModel.openClearLogs() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shapes = ToggleButtonDefaults.shapes(
                        shape = clearShape,
                        pressedShape = clearShape,
                        checkedShape = clearShape
                    ),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(
                        text = Locales.getString("settings", "logs", "clear", "button"),
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (state.dialog == ProfileActionDialog.ClearLogs) {
        DestructiveConfirmationDialog(
            title = Locales.getString("settings", "logs", "clear", "button"),
            body = Locales.getString("settings", "logs", "clear", "confirm", "text"),
            confirmLabel = Locales.getString("settings", "logs", "clear", "confirm", "submit"),
            submitting = state.inFlight == ProfileAction.ClearLogs,
            errorMessage = state.errorMessage,
            onConfirm = { viewModel.clearLogs(profile.id) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileActionsSection(
    profile: NextDnsProfile,
    capabilities: ProfileCapabilities,
    onProfileRemoved: (String) -> Unit,
    onProfileListChanged: () -> Unit,
    onOpenProfileSettings: () -> Unit,
) {
    val epoch = rememberAccountEpoch()
    val viewModel: ProfileActionsViewModel =
        viewModel(key = "profile-actions-${epoch.sequence}-${profile.id}")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(capabilities) {
        viewModel.setCapabilities(capabilities)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileActionsEffect.Message ->
                    Toast.makeText(context, effect.value, Toast.LENGTH_SHORT).show()

                is ProfileActionsEffect.ProfileRemoved -> onProfileRemoved(effect.profileId)
                ProfileActionsEffect.ProfileDuplicated -> {
                    onProfileListChanged()
                    onOpenProfileSettings()
                }
                ProfileActionsEffect.ProfileRenamed -> onProfileListChanged()
                ProfileActionsEffect.LogsCleared -> Unit
            }
        }
    }

    Column {
        val actionKeys = buildList {
            add("duplicate")
            if (capabilities.canEditSettings) add("rename")
            if (capabilities.canDelete || capabilities.canLeave) add("delete")
        }

        if (actionKeys.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val cornerRadius = 12.dp
                actionKeys.forEachIndexed { index, key ->
                    val shape = when {
                        actionKeys.size == 1 -> RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
                        index == 0 -> RoundedCornerShape(bottomStart = cornerRadius, topStart = cornerRadius)
                        index == actionKeys.size - 1 -> RoundedCornerShape(bottomEnd = cornerRadius, topEnd = cornerRadius)
                        else -> RoundedCornerShape(0.dp)
                    }
                    val shapes = ToggleButtonDefaults.shapes(
                        shape = shape,
                        pressedShape = shape,
                        checkedShape = shape
                    )

                    val isDestructive = key == "delete"
                    val label = when (key) {
                        "duplicate" -> Locales.getString("settings", "duplicate", "form", "button")
                        "rename" -> stringResource(R.string.rename)
                        "delete" -> Locales.getString("global", if (capabilities.canDelete) "delete" else "leave")
                        else -> ""
                    }
                    val icon = when (key) {
                        "duplicate" -> Icons.Default.ContentCopy
                        "rename" -> Icons.Default.Edit
                        "delete" -> Icons.Default.Delete
                        else -> Icons.Default.Delete
                    }

                    ToggleButton(
                        checked = false,
                        onCheckedChange = {
                            when (key) {
                                "duplicate" -> viewModel.openDuplicate()
                                "rename" -> viewModel.openRename(profile.name)
                                "delete" -> viewModel.openDeleteOrLeave()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shapes = shapes,
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(label)
                    }
                }
            }
        }
    }

    when (state.dialog) {
        ProfileActionDialog.Duplicate -> ProfileNameDialog(
            title = Locales.getString("settings", "duplicate", "form", "button"),
            label = Locales.getString("settings", "duplicate", "form", "name", "label"),
            placeholder = Locales.getString("settings", "duplicate", "form", "name", "placeholder"),
            value = state.duplicateName,
            error = state.duplicateNameError,
            submitting = state.inFlight == ProfileAction.Duplicate,
            errorMessage = state.errorMessage,
            confirmLabel = Locales.getString("settings", "duplicate", "form", "button"),
            onValueChange = viewModel::updateDuplicateName,
            onConfirm = { viewModel.duplicateProfile(profile.id) },
            onDismiss = viewModel::dismissDialog,
        )

        ProfileActionDialog.Rename -> ProfileNameDialog(
            title = stringResource(R.string.rename_profile),
            label = Locales.getString("settings", "duplicate", "form", "name", "label"),
            placeholder = Locales.getString("settings", "duplicate", "form", "name", "placeholder"),
            value = state.renameName,
            error = state.renameNameError,
            submitting = state.inFlight == ProfileAction.Rename,
            errorMessage = state.errorMessage,
            confirmLabel = Locales.getString("global", "save"),
            onValueChange = viewModel::updateRenameName,
            onConfirm = { viewModel.renameProfile(profile.id) },
            onDismiss = viewModel::dismissDialog,
        )

        ProfileActionDialog.DeleteOrLeave -> {
            val isDelete = capabilities.canDelete
            val section = if (isDelete) "delete" else "leave"
            DestructiveConfirmationDialog(
                title = Locales.getPlainString(
                    arrayOf("settings", section, "button"),
                    mapOf("name" to profile.name),
                ),
                body = Locales.getPlainString(
                    arrayOf("settings", section, "confirm", "text"),
                    mapOf("name" to profile.name),
                ),
                confirmLabel = Locales.getString("global", if (isDelete) "delete" else "leave"),
                submitting = state.inFlight == ProfileAction.DeleteOrLeave,
                errorMessage = state.errorMessage,
                onConfirm = { viewModel.deleteOrLeaveProfile(profile.id) },
                onDismiss = viewModel::dismissDialog,
            )
        }

        else -> Unit
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    label: String,
    placeholder: String,
    value: String,
    error: ProfileNameError?,
    submitting: Boolean,
    errorMessage: String?,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = title,
        confirmLabel = confirmLabel,
        submitting = submitting,
        errorMessage = errorMessage,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = error != null,
            enabled = !submitting,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                error?.let {
                    Text(
                        when (it) {
                            ProfileNameError.Required -> Locales.getString(
                                "settings", "duplicate", "form", "name", "errors", "required"
                            )

                            ProfileNameError.Duplicate -> Locales.getString(
                                "settings", "duplicate", "form", "name", "errors", "taken"
                            )
                        }
                    )
                }
            },
        )
    }
}
