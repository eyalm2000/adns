package com.eyalm.adns.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyalm.adns.R
import com.eyalm.adns.ui.components.rememberAccountEpoch
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.recreation.RecreationScheduleError
import com.eyalm.adns.data.nextdns.recreation.RecreationScheduleValidation
import com.eyalm.adns.data.nextdns.recreation.RecreationTimeDraft
import com.eyalm.adns.ui.components.ExpandableResourceSettingRow
import com.eyalm.adns.ui.components.ExpressiveCard
import com.eyalm.adns.ui.components.ExpressiveCardHeader
import com.eyalm.adns.ui.components.ResourceSettingRow
import com.eyalm.adns.ui.components.dialogs.FormDialog
import com.eyalm.adns.ui.components.segmentPosition
import com.eyalm.adns.viewmodel.nextdns.RecreationUiState
import com.eyalm.adns.viewmodel.nextdns.RecreationViewModel
import java.util.Locale

@Composable
fun RecreationSection(
    profileId: String,
    canEdit: Boolean,
    refreshRevision: Long = 0,
) {
    val epoch = rememberAccountEpoch()
    val viewModel: RecreationViewModel = viewModel(key = "recreation-${epoch.sequence}-$profileId")
    val state by viewModel.state.collectAsState()

    LaunchedEffect(profileId, refreshRevision, canEdit) {
        viewModel.load(profileId, canEdit)
    }

    ExpressiveCard {
        ExpressiveCardHeader(
            title = Locales.getString("parentalControl", "recreation", "name"),
            description = Locales.getString("parentalControl", "recreation", "description"),
            trailing = {
            IconButton(onClick = viewModel::openEditor, enabled = canEdit) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = Locales.getString("global", "add"),
                )
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
            val activeDays = RecreationScheduleValidation.days.filter { it in state.schedule.times }

            if (!state.editorOpen) {
                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (activeDays.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                activeDays.forEachIndexed { index, day ->
                    state.schedule.times[day]?.let { window ->
                        ResourceSettingRow(
                            onClick = { if (canEdit) viewModel.openEditor() },
                            title = recreationDayName(day),
                            trailing = {
                                Text(
                                    text = stringResource(
                                        R.string.recreation_time_range,
                                        window.start.take(5),
                                        window.end.take(5),
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            position = segmentPosition(index, activeDays.size),
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }

    if (state.editorOpen) {
        RecreationScheduleDialog(
            state = state,
            onStartChange = viewModel::updateStart,
            onEndChange = viewModel::updateEnd,
            onDayEnabledChange = viewModel::setDayEnabled,
            onSave = viewModel::saveSchedule,
            onDismiss = viewModel::dismissEditor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecreationScheduleDialog(
    state: RecreationUiState,
    onStartChange: (String, String) -> Unit,
    onEndChange: (String, String) -> Unit,
    onDayEnabledChange: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingTime by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var expandedDay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.scheduleErrors) {
        val firstInvalidDay = RecreationScheduleValidation.days.firstOrNull { it in state.scheduleErrors }
        if (firstInvalidDay != null) {
            pickingTime = null
            expandedDay = firstInvalidDay
        }
    }

    FormDialog(
        title = Locales.getString("parentalControl", "recreation", "set"),
        body = stringResource(R.string.configure_the_time_window_for_each_day),
        confirmLabel = Locales.getString("global", "save"),
        submitting = state.savingSchedule,
        errorMessage = state.errorMessage,
        modifier = Modifier.fillMaxWidth(0.83f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onConfirm = onSave,
        onDismiss = onDismiss,
    ) {
        Spacer(Modifier.height(20.dp))
        RecreationScheduleValidation.days.forEach { day ->
            val draft = state.draftTimes[day] ?: RecreationTimeDraft()
            val isError = day in state.scheduleErrors
            val isExpanded = expandedDay == day

            Column {
                ExpandableResourceSettingRow(
                    title = recreationDayName(day),
                    description = if (!draft.enabled)
                        stringResource(R.string.recreation_time_disabled)
                    else
                        stringResource(R.string.recreation_time_range, draft.start, draft.end),
                    onClick = {
                        if (!state.savingSchedule) {
                            pickingTime = null
                            if (isExpanded) {
                                if (draft.start.isBlank() && draft.end.isBlank()) {
                                    onDayEnabledChange(day, false)
                                }
                                expandedDay = null
                            } else {
                                if (!draft.enabled) {
                                    onDayEnabledChange(day, true)
                                }
                                expandedDay = day
                            }
                        }
                    },
                    expanded = isExpanded,
                    position = segmentPosition(
                        RecreationScheduleValidation.days.indexOf(day),
                        RecreationScheduleValidation.days.size,
                    ),
                    trailing = {
                        Switch(
                            checked = draft.enabled,
                            enabled = !state.savingSchedule,
                            onCheckedChange = { enabled ->
                                onDayEnabledChange(day, enabled)
                                pickingTime = null
                                expandedDay = when {
                                    enabled -> day
                                    expandedDay == day -> null
                                    else -> expandedDay
                                }
                            },
                        )
                    },
                    content = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimeSelectionButton(
                                    label = stringResource(R.string.recreation_time_start),
                                    time = draft.start,
                                    enabled = !state.savingSchedule,
                                    onClick = { pickingTime = day to false }
                                )
                                Text(
                                    "–",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TimeSelectionButton(
                                    label = stringResource(R.string.recreation_time_end),
                                    time = draft.end,
                                    enabled = !state.savingSchedule,
                                    onClick = { pickingTime = day to true }
                                )
                            }

                            if (isError) {
                                Text(
                                    text = recreationScheduleErrorText(state.scheduleErrors[day]),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                )
            }
            if (day != RecreationScheduleValidation.days.last()) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    pickingTime?.let { (day, isEnd) ->
        key(day, isEnd) {
            val currentTime = if (isEnd) state.draftTimes[day]?.end else state.draftTimes[day]?.start
            val (h, m) = parseTime(currentTime.orEmpty())

            TimePickerDialog(
                title = stringResource(
                    if (isEnd) R.string.recreation_time_end_title else R.string.recreation_time_start_title
                ),
                initialHour = h,
                initialMinute = m,
                onConfirm = { hour, minute ->
                    val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                    if (isEnd) onEndChange(day, formatted) else onStartChange(day, formatted)
                    pickingTime = null
                },
                onDismiss = { pickingTime = null }
            )
        }
    }
}

@Composable
private fun recreationScheduleErrorText(error: RecreationScheduleError?): String = when (error) {
    RecreationScheduleError.BothTimesRequired -> stringResource(R.string.recreation_time_error_both_required)
    RecreationScheduleError.InvalidTime -> stringResource(R.string.recreation_time_error_invalid)
    RecreationScheduleError.EndMustFollowStart -> stringResource(R.string.recreation_time_error_end_before_start)
    null -> ""
}

@Composable
private fun TimeSelectionButton(
    label: String,
    time: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.height(56.dp)
        ) {
            Text(
                text = time?.takeIf { it.isNotBlank() } ?: "--:--",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(Locales.getString("global", "save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Locales.getString("global", "cancel"))
            }
        },
        title = { Text(title) },
        text = {
            TimePicker(state = state)
        }
    )
}

private fun parseTime(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    if (parts.size >= 2) {
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0
        return hour to minute
    }
    return 0 to 0
}

@Composable
private fun recreationDayName(day: String): String = stringResource(
    when (day) {
        "monday" -> R.string.recreation_day_monday
        "tuesday" -> R.string.recreation_day_tuesday
        "wednesday" -> R.string.recreation_day_wednesday
        "thursday" -> R.string.recreation_day_thursday
        "friday" -> R.string.recreation_day_friday
        "saturday" -> R.string.recreation_day_saturday
        "sunday" -> R.string.recreation_day_sunday
        else -> R.string.recreation_day_unknown
    }
)
