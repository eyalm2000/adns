package com.eyalm.adns.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.ui.components.rememberAccountEpoch
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.access.AccessEntry
import com.eyalm.adns.data.nextdns.access.AccessError
import com.eyalm.adns.data.nextdns.access.AccessField
import com.eyalm.adns.data.nextdns.access.AccessRole
import com.eyalm.adns.ui.components.ExpressiveCard
import com.eyalm.adns.ui.components.ExpressiveCardHeader
import com.eyalm.adns.ui.components.RadioSettingRow
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.SegmentPosition
import com.eyalm.adns.ui.components.dialogs.DestructiveConfirmationDialog
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.viewmodel.nextdns.AccessUiState
import com.eyalm.adns.viewmodel.nextdns.AccessViewModel

@Composable
fun AccessSection(
    profileId: String,
    refreshRevision: Long = 0,
) {
    val epoch = rememberAccountEpoch()
    val viewModel: AccessViewModel = viewModel(key = "access-${epoch.sequence}-$profileId")
    val state by viewModel.state.collectAsState()

    LaunchedEffect(profileId, refreshRevision) {
        viewModel.load(profileId, canManage = true)
    }

    ExpressiveCard {
        ExpressiveCardHeader(
            title = Locales.getString("settings", "access", "name"),
            description = Locales.getString("settings", "access", "description"),
            isBeta = true,
            trailing = {
                IconButton(onClick = viewModel::openInvite) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = Locales.getString("global", "add"))
                }
            },
        )
        if (!state.initialLoadComplete) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (!state.inviteDialogOpen && state.roleTarget == null && state.deleting == null) {
                state.errorMessage?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
            }
            state.items.forEachIndexed { index, entry ->
                AccessRow(
                    entry = entry,
                    position = segmentPosition(index, state.items.size),
                    onRoleClick = { viewModel.requestRoleChange(entry) },
                    onDelete = { viewModel.requestDelete(entry) },
                )
            }
        }
    }

    if (state.inviteDialogOpen) {
        InviteAccessDialog(
            state = state,
            onEmailChange = viewModel::updateEmail,
            onRoleChange = viewModel::updateInviteRole,
            onSubmit = viewModel::submitInvite,
            onDismiss = viewModel::dismissInvite,
        )
    }

    state.roleTarget?.let { target ->
        AccessRoleDialog(
            email = target.email,
            selectedRole = state.selectedRole,
            submitting = state.submitting,
            errorMessage = state.errorMessage,
            onRoleChange = viewModel::updateSelectedRole,
            onSubmit = viewModel::submitRoleChange,
            onDismiss = viewModel::dismissRoleChange,
        )
    }

    state.deleting?.let { entry ->
        DestructiveConfirmationDialog(
            title = Locales.getString("global","remove"),
            body = stringResource(R.string.remove, entry.email),
            confirmLabel = Locales.getString("global", "remove"),
            submitting = state.submitting,
            errorMessage = state.errorMessage,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

}

@Composable
private fun InviteAccessDialog(
    state: AccessUiState,
    onEmailChange: (String) -> Unit,
    onRoleChange: (AccessRole) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = Locales.getString("global", "invite"),
        confirmLabel = Locales.getString("global", "invite"),
        submitting = state.submitting,
        errorMessage = state.errorMessage,
        onConfirm = onSubmit,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(Locales.getString("settings", "access", "form", "email", "label")) },
            isError = AccessField.Email in state.fieldErrors,
            enabled = !state.submitting,
            supportingText = {
                state.fieldErrors[AccessField.Email]?.let { error ->
                    Text(accessErrorText(error))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            )
        AccessRoleChoices(
            selectedRole = state.inviteRole,
            enabled = !state.submitting,
            onRoleChange = onRoleChange,
        )
    }
}

@Composable
private fun AccessRoleDialog(
    email: String,
    selectedRole: AccessRole,
    submitting: Boolean,
    errorMessage: String?,
    onRoleChange: (AccessRole) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = Locales.getString("settings", "access", "form", "role", "label"),
        confirmLabel = Locales.getString("global", "save"),
        submitting = submitting,
        errorMessage = errorMessage,
        onConfirm = onSubmit,
        onDismiss = onDismiss,

    ) {
        Text(email, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        AccessRoleChoices(
            selectedRole = selectedRole,
            enabled = !submitting,
            onRoleChange = onRoleChange,
        )
    }
}

@Composable
private fun AccessRoleChoices(
    selectedRole: AccessRole,
    enabled: Boolean,
    onRoleChange: (AccessRole) -> Unit,
) {
    AccessRole.entries.forEachIndexed { index, role ->
        RadioSettingRow(
            title = role.label(),
            selected =role == selectedRole,
            onClick = { onRoleChange(role) },
            position = segmentPosition(index, AccessRole.entries.size),
            enabled = enabled,
            radio = { selected, onClick ->
                RadioButton(
                    selected = selected,
                    enabled = enabled,
                    onClick = onClick,
                )
            },
        )
    }
}

@Composable
private fun AccessRow(
    entry: AccessEntry,
    position: SegmentPosition,
    onRoleClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ResourceSettingRow(
        title = entry.email,
        selected = true,
        description = buildString {
            append(entry.role.label())
            if (entry.pending) {
                append(" · ")
                append(Locales.getString("settings", "access", "pending"))
            }
        },
        onClick = onRoleClick,
        trailing = {
            Row {
                TextButton(onClick = onRoleClick) {
                    Text(entry.role.label())
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = Locales.getString("global", "remove"))
                }
            }
        },
        position = position,
    )
    Spacer(Modifier.height(2.dp))
}

private fun AccessRole.label(): String =
    Locales.getString("settings", "access", "roles", name.lowercase())

private fun accessErrorText(error: AccessError): String = when (error) {
    AccessError.Required ->
        Locales.getString("settings", "access", "form", "email", "errors", "required")
    AccessError.Invalid ->
        Locales.getString("settings", "access", "form", "email", "errors", "invalid")
    AccessError.Duplicate ->
        Locales.getString("settings", "access", "form", "email", "errors", "duplicate")
}
