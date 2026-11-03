package com.eyalm.adns.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eyalm.adns.R
import com.eyalm.adns.data.nextdns.analytics.StatRow
import com.eyalm.adns.data.nextdns.api.TrackerInfo
import com.eyalm.adns.data.nextdns.model.ListIcon
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatItemDetailsBottomSheet(
    item: StatRow,
    trackerInfo: TrackerInfo? = null,
    trackerLoading: Boolean = false,
    canToggleGraph: Boolean = false,
    isGraphEnabled: Boolean = false,
    graphColor: Color? = null,
    onToggleGraph: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item.icon !is ListIcon.None) {
                    ListIconView(
                        icon = item.icon,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (item.highlightDomain) {
                        DomainTitleText(
                            domain = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                        )
                    } else {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    val rawQueriesFormatted = if (item.rawQueries > 0) {
                        NumberFormat.getNumberInstance(Locale.US).format(item.rawQueries)
                    } else item.value
                    BottomSheetDetailRow(
                        icon = Icons.Outlined.BarChart,
                        label = stringResource(R.string.queries_label) + ":",
                        value = rawQueriesFormatted
                    )

                    item.percentage?.let { pct ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Info,
                            label = stringResource(R.string.percentage) + ":",
                            value = String.format(Locale.US, "%.1f%%", pct)
                        )
                    }

                    // ISP
                    item.isp?.takeIf(String::isNotBlank)?.let { isp ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Info,
                            label = stringResource(R.string.isp) + ":",
                            value = isp
                        )
                    }

                    item.location?.takeIf(String::isNotBlank)?.let { loc ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Place,
                            label = stringResource(R.string.location) + ":",
                            value = loc
                        )
                    }

                    item.isCellular?.let { isCellular ->
                        BottomSheetDetailRow(
                            icon = if (isCellular) Icons.Outlined.SignalCellularAlt else Icons.Outlined.Wifi,
                            label = stringResource(R.string.network) + ":",
                            value = if (isCellular) stringResource(R.string.cellular) else stringResource(R.string.wifi_broadband)
                        )
                    }

                    item.countryCode?.takeIf(String::isNotBlank)?.let { code ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Public,
                            label = stringResource(R.string.country_code) + ":",
                            value = code
                        )
                    }

                    item.tracker?.takeIf(String::isNotBlank)?.let { trackerId ->
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Security,
                            label = stringResource(R.string.tracker) + ":",
                            value = trackerId
                        )
                    }

                    if (item.topDomains.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.top_domains),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            item.topDomains.forEach { domain ->

                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 32.dp)
                                )

                            }
                        }
                    }

                    if (item.id.isNotEmpty() && item.id != item.title && item.countryCode == null) {
                        BottomSheetDetailRow(
                            icon = Icons.Outlined.Fingerprint,
                            label = stringResource(R.string.id_label) + ":",
                            value = item.id
                        )
                    }
                }
            }

            // Tracker Insights Section
            if (trackerLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else if (trackerInfo != null) {
                TrackerInsightsCard(tracker = trackerInfo)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canToggleGraph) {
                    Button(
                        onClick = onToggleGraph,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGraphEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isGraphEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        )
                    ) {
                        if (isGraphEnabled && graphColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(graphColor)
                            )
                        } else {
                            Icon(
                                imageVector = if (isGraphEnabled) Icons.Filled.Check else Icons.AutoMirrored.Outlined.ShowChart,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isGraphEnabled) stringResource(R.string.in_graph) else stringResource(R.string.show_on_graph),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Button(
                    onClick = { onCopy(item.title) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.copy),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

