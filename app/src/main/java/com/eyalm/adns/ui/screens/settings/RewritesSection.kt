package com.eyalm.adns.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.eyalm.adns.data.nextdns.rewrites.Rewrite
import com.eyalm.adns.data.nextdns.rewrites.RewriteError
import com.eyalm.adns.data.nextdns.rewrites.RewriteField
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.SegmentPosition
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.ui.components.ExpressiveCard
import com.eyalm.adns.ui.components.ExpressiveCardHeader
import com.eyalm.adns.ui.components.dialogs.DestructiveConfirmationDialog
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.viewmodel.nextdns.RewritesUiState
import com.eyalm.adns.viewmodel.nextdns.RewritesViewModel

@Composable
fun RewritesSection(
    profileId: String,
    canEdit: Boolean,
    refreshRevision: Long = 0,
) {
    val epoch = rememberAccountEpoch()
    val viewModel: RewritesViewModel = viewModel(key = "rewrites-${epoch.sequence}-$profileId")
    val state by viewModel.state.collectAsState()

    LaunchedEffect(profileId, refreshRevision, canEdit) {
        viewModel.load(profileId, canEdit)
    }

    ExpressiveCard {
        ExpressiveCardHeader(
            title = Locales.getString("settings", "rewrites", "name"),
            description = Locales.getString("settings", "rewrites", "description"),
            trailing = {
            Row {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = viewModel::openCreate, enabled = canEdit) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = Locales.getString("global", "add")
                    )
                }
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
            if (!state.createDialogOpen && state.deleting == null) {
                state.errorMessage?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
            }
            state.items.forEachIndexed { index, rewrite ->
                RewriteRow(
                    rewrite = rewrite,
                    position = segmentPosition(index, state.items.size),
                    onDelete = { viewModel.requestDelete(rewrite) },
                    canEdit = canEdit
                )
            }
        }
    }

    if (state.createDialogOpen) {
        RewriteFormDialog(
            state = state,
            onNameChange = viewModel::updateName,
            onContentChange = viewModel::updateContent,
            onSubmit = viewModel::submitCreate,
            onDismiss = viewModel::dismissCreate,
        )
    }

    state.deleting?.let { rewrite ->
        DestructiveConfirmationDialog(
            title = Locales.getString("global", "remove"),
            body = stringResource(R.string.remove, rewrite.name),
            confirmLabel = Locales.getString("global", "remove"),
            submitting = state.submitting,
            errorMessage = state.errorMessage,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun RewriteFormDialog(
    state: RewritesUiState,
    onNameChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = Locales.getString("settings", "rewrites", "new"),
        confirmLabel = Locales.getString("global", "save"),
        submitting = state.submitting,
        errorMessage = state.errorMessage,
        onConfirm = onSubmit,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = {
                Text(Locales.getString("settings", "rewrites", "form", "domain", "label"))
            },
            placeholder = {
                Text(Locales.getString("settings", "rewrites", "form", "domain", "placeholder"))
            },
            isError = RewriteField.Name in state.fieldErrors,
            enabled = !state.submitting,
            supportingText = {
                state.fieldErrors[RewriteField.Name]?.let { error ->
                    Text(rewriteErrorText(RewriteField.Name, error))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = state.content,
            onValueChange = onContentChange,
            label = {
                Text(Locales.getString("settings", "rewrites", "form", "answer", "label"))
            },
            placeholder = {
                Text(Locales.getString("settings", "rewrites", "form", "answer", "placeholder"))
            },
            isError = RewriteField.Content in state.fieldErrors,
            enabled = !state.submitting,
            supportingText = {
                state.fieldErrors[RewriteField.Content]?.let { error ->
                    Text(rewriteErrorText(RewriteField.Content, error))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun RewriteRow(
    rewrite: Rewrite,
    position: SegmentPosition,
    onDelete: () -> Unit,
    canEdit: Boolean
) {
    ResourceSettingRow(
        title = "*.${rewrite.name} → ${rewrite.content}",
        selected = true,
        description = rewrite.type,
        onClick = {},
        trailing = {
            IconButton(onClick = onDelete, enabled = canEdit) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = Locales.getString("global", "remove")
                )
            }
        },
        position = position,
    )
    Spacer(Modifier.height(2.dp))
}

private fun rewriteErrorText(
    field: RewriteField,
    error: RewriteError,
): String = when (field to error) {
    RewriteField.Name to RewriteError.Required ->
        Locales.getString("settings", "rewrites", "form", "domain", "errors", "required")
    RewriteField.Name to RewriteError.Ip ->
        Locales.getString("settings", "rewrites", "form", "domain", "errors", "ip")
    RewriteField.Name to RewriteError.Invalid ->
        Locales.getString("settings", "rewrites", "form", "domain", "errors", "invalid")
    RewriteField.Name to RewriteError.Taken ->
        Locales.getString("settings", "rewrites", "form", "domain", "errors", "taken")
    RewriteField.Content to RewriteError.Required ->
        Locales.getString("settings", "rewrites", "form", "answer", "errors", "required")
    RewriteField.Content to RewriteError.Invalid ->
        Locales.getString("settings", "rewrites", "form", "answer", "errors", "invalid")
    else -> error.name
}
