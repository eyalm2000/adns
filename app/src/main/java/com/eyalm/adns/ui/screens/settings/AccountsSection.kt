package com.eyalm.adns.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eyalm.adns.R
import com.eyalm.adns.data.account.AccountSummary
import com.eyalm.adns.data.account.AccountsSnapshot
import com.eyalm.adns.ui.components.dialogs.DestructiveConfirmationDialog
import com.eyalm.adns.ui.components.dialogs.FormDialog

/**
 * The configured accounts, with the actions that apply to them. Only labels, status and profile
 * ids are shown; credentials never reach the UI.
 */
@Composable
fun AccountsSection(
    accounts: AccountsSnapshot,
    busy: Boolean,
    onSwitch: (accountId: String) -> Unit,
    onRename: (accountId: String, label: String) -> Unit,
    onRemove: (accountId: String) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<AccountSummary?>(null) }
    var removeTarget by remember { mutableStateOf<AccountSummary?>(null) }
    val names = accountDisplayNames(accounts.accounts, stringResource(R.string.account_unnamed))

    renameTarget?.let { target ->
        RenameAccountDialog(
            currentName = target.label,
            onConfirm = { label ->
                renameTarget = null
                onRename(target.id, label)
            },
            onDismiss = { renameTarget = null },
        )
    }
    removeTarget?.let { target ->
        DestructiveConfirmationDialog(
            title = stringResource(R.string.account_remove_title),
            body = stringResource(R.string.account_remove_message, names[target.id].orEmpty()),
            confirmLabel = stringResource(R.string.account_remove),
            onConfirm = {
                removeTarget = null
                onRemove(target.id)
            },
            onDismiss = { removeTarget = null },
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            accounts.accounts.forEachIndexed { index, account ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                AccountRow(
                    account = account,
                    name = names[account.id].orEmpty(),
                    isActive = account.id == accounts.activeAccountId,
                    busy = busy,
                    onSwitch = { onSwitch(account.id) },
                    onSignInAgain = onAddAccount,
                    onRename = { renameTarget = account },
                    onRemove = { removeTarget = account },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAddAccount,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.add_account), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountSummary,
    name: String,
    isActive: Boolean,
    busy: Boolean,
    onSwitch: () -> Unit,
    onSignInAgain: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = stringResource(
        when {
            !account.hasCredential -> R.string.account_status_needs_sign_in
            isActive -> R.string.account_status_active
            else -> R.string.account_status_signed_in
        }
    )
    val profile = account.selectedProfileId
        ?.let { stringResource(R.string.account_profile_id, it) }
        ?: stringResource(R.string.account_no_profile)
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = "$status · $profile",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!account.hasCredential) {
                TextButton(onClick = onSignInAgain, enabled = !busy) {
                    Text(stringResource(R.string.account_sign_in_again))
                }
            } else if (!isActive) {
                TextButton(onClick = onSwitch, enabled = !busy) {
                    Text(stringResource(R.string.account_switch))
                }
            }
            TextButton(onClick = onRename, enabled = !busy) {
                Text(stringResource(R.string.account_rename))
            }
            TextButton(onClick = onRemove, enabled = !busy) {
                Text(
                    text = stringResource(R.string.account_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameAccountDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    FormDialog(
        title = stringResource(R.string.account_rename_title),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = name.trim().isNotEmpty(),
        onConfirm = { if (name.trim().isNotEmpty()) onConfirm(name) },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.account_name)) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
    }
}

/**
 * A name for every account that tells them apart. Two accounts that share a name (for example
 * two API-key sign-ins) get a number, in the order they are stored, so none looks like another.
 */
internal fun accountDisplayNames(
    accounts: List<AccountSummary>,
    unnamed: String,
): Map<String, String> {
    val base = accounts.associate { it.id to it.label.trim().ifEmpty { unnamed } }
    val totals = base.values.groupingBy { it }.eachCount()
    val seen = HashMap<String, Int>()
    return accounts.associate { account ->
        val name = base.getValue(account.id)
        val number = (seen[name] ?: 0) + 1
        seen[name] = number
        account.id to if (totals.getValue(name) > 1) "$name ($number)" else name
    }
}
