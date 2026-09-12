package com.eyalm.adns.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.settings.FeatureMaturity

enum class SegmentPosition {
    Single,
    First,
    Middle,
    Last,
}

fun segmentPosition(index: Int, size: Int): SegmentPosition {
    require(size > 0) { "A segmented group must contain at least one item." }
    if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index, size=$size")
    return when {
        size == 1 -> SegmentPosition.Single
        index == 0 -> SegmentPosition.First
        index == size - 1 -> SegmentPosition.Last
        else -> SegmentPosition.Middle
    }
}

private fun SegmentPosition.shape() = RoundedCornerShape(
    topStart = if (this == SegmentPosition.Single || this == SegmentPosition.First) 12.dp else 2.dp,
    topEnd = if (this == SegmentPosition.Single || this == SegmentPosition.First) 12.dp else 2.dp,
    bottomStart = if (this == SegmentPosition.Single || this == SegmentPosition.Last) 12.dp else 2.dp,
    bottomEnd = if (this == SegmentPosition.Single || this == SegmentPosition.Last) 12.dp else 2.dp,
)

@Composable
fun MaturityBadge(
    maturity: FeatureMaturity,
    modifier: Modifier = Modifier,
) {
    if (maturity == FeatureMaturity.STABLE) return
    val (label, containerColor, contentColor) = when (maturity) {
        FeatureMaturity.BETA -> {
            val betaLabel = Locales.getString("global", "beta")
                .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                ?: Locales.getString("beta")
                    .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                ?: "Beta"
            val formatted = betaLabel.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString()
            }
            Triple(
                formatted,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        FeatureMaturity.EARLY_ACCESS -> {
            val previewLabel = Locales.getString("global", "preview")
                .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                ?: "Early Access"
            Triple(
                previewLabel,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        FeatureMaturity.UNRELEASED -> {
            val unreleasedLabel = stringResource(R.string.unreleased)
            Triple(
                unreleasedLabel,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        FeatureMaturity.STABLE -> return
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun BetaBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val formattedBeta = text.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString()
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = formattedBeta,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    position: SegmentPosition = SegmentPosition.Single,
    shape: Shape? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    alignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    indicatorColor: Color? = null,
    selectedColor: Color? = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
    onClick: () -> Unit = {},
) {
    val rowShape = shape ?: position.shape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val itemColors = ListItemDefaults.colors(containerColor = containerColor,
        selectedContainerColor = selectedColor ?: MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))

    SegmentedListItem(
        selected = selected,
        onClick = { if (enabled) onClick() },
        shapes = ListItemDefaults.shapes(shape = rowShape, selectedShape = rowShape),
        colors = itemColors,
        leadingContent = leading,
        trailingContent = trailing,
        verticalAlignment = alignment,
        supportingContent = if (description != null || supporting != null) {
            {
                Column {
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    supporting?.invoke()
                }
            }
        } else {
            null
        },
        modifier = modifier.then(
            if (indicatorColor != null) {
                Modifier
                    .clip(rowShape)
                    .drawWithContent {
                        drawContent()
                        val widthPx = 4.dp.toPx()
                        drawRect(
                            color = indicatorColor,
                            topLeft = Offset.Zero,
                            size = Size(widthPx, size.height)
                        )
                    }
            } else Modifier
        ),
        content = {
            val effectiveMaturity = if (maturity != FeatureMaturity.STABLE) maturity else if (isBeta) FeatureMaturity.BETA else FeatureMaturity.STABLE
            if (titleContent != null) {
                titleContent.invoke()
            } else if (effectiveMaturity != FeatureMaturity.STABLE) {
                val badgeLabel = when (effectiveMaturity) {
                    FeatureMaturity.BETA -> {
                        val betaLabel = Locales.getString("global", "beta")
                            .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                            ?: Locales.getString("beta")
                                .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                            ?: "Beta"
                        betaLabel.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString()
                        }
                    }
                    FeatureMaturity.EARLY_ACCESS -> {
                        Locales.getString("global", "preview")
                            .takeIf { !it.startsWith("[missing:") && it.isNotBlank() }
                            ?: "Early Access"
                    }
                    FeatureMaturity.UNRELEASED -> {
                        stringResource(R.string.unreleased)
                    }
                    FeatureMaturity.STABLE -> ""
                }
                val maturityBadgeId = "maturityBadge"
                val annotatedTitle = remember(title, badgeLabel) {
                    buildAnnotatedString {
                        append(title)
                        append("  ")
                        appendInlineContent(maturityBadgeId, "[$badgeLabel]")
                    }
                }
                val inlineContentMap = remember(badgeLabel, effectiveMaturity) {
                    mapOf(
                        maturityBadgeId to InlineTextContent(
                            Placeholder(
                                width = (badgeLabel.length * 0.65 + 1.4).em,
                                height = 1.3.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MaturityBadge(maturity = effectiveMaturity)
                            }
                        }
                    )
                }
                Text(
                    text = annotatedTitle,
                    inlineContent = inlineContentMap,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
fun NavigationSettingRow(
    title: String,
    description: String? = null,
    position: SegmentPosition = SegmentPosition.Single,
    enabled: Boolean = true,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) = SegmentedSettingRow(
    title = title,
    description = description,
    position = position,
    enabled = enabled,
    maturity = maturity,
    isBeta = isBeta,
    leading = leading?.let {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                it()
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    },
    trailing = trailing,
    alignment = Alignment.CenterVertically,
    onClick = onClick,
)


@Composable
fun ToggleSettingRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    position: SegmentPosition = SegmentPosition.Single,
    enabled: Boolean = true,
    saving: Boolean = false,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    toggle: @Composable (Boolean, (Boolean) -> Unit) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val canChange = enabled && !saving
    SegmentedSettingRow(
        title = title,
        description = description,
        position = position,
        enabled = enabled,
        selected = checked,
        maturity = maturity,
        isBeta = isBeta,
        leading = leading,
        alignment = Alignment.CenterVertically,
        trailing = { toggle(checked) { if (canChange) onCheckedChange(it) } },
        onClick = { if (canChange) onCheckedChange(!checked) },
    )
}

@Composable
fun RadioSettingRow(
    title: String,
    description: String? = null,
    selected: Boolean, // or to enable second color
    position: SegmentPosition = SegmentPosition.Single,
    enabled: Boolean = true,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    radio: @Composable (Boolean, () -> Unit) -> Unit,
    onClick: () -> Unit,
) = SegmentedSettingRow(
    title = title,
    description = description,
    position = position,
    enabled = enabled,
    selected = selected,
    maturity = maturity,
    isBeta = isBeta,
    trailing = { // prev leading
        radio(selected) { if (enabled) onClick() }
    },
    onClick = onClick,
    alignment = Alignment.CenterVertically
)

@Composable
fun ActionSettingRow(
    title: String,
    description: String? = null,
    position: SegmentPosition = SegmentPosition.Single,
    enabled: Boolean = true,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) = SegmentedSettingRow(
    title = title,
    description = description,
    position = position,
    enabled = enabled,
    maturity = maturity,
    isBeta = isBeta,
    leading = leading,
    trailing = trailing,
    onClick = onClick,
)


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResourceSettingRow(
    title: String,
    description: String? = null,
    position: SegmentPosition = SegmentPosition.Single,
    shape: Shape? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    alignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    indicatorColor: Color? = null,
    selectedColor: Color? = null,
    onClick: () -> Unit = {},
) = SegmentedSettingRow(
    title = title,
    description = description,
    position = position,
    shape = shape,
    enabled = enabled,
    selected = selected,
    maturity = maturity,
    isBeta = isBeta,
    leading = leading,
    trailing = trailing,
    alignment = alignment,
    supporting = supporting,
    titleContent = titleContent,
    indicatorColor = indicatorColor,
    selectedColor = selectedColor,
    onClick = onClick,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableResourceSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    expanded: Boolean = false,
    selected: Boolean = expanded,
    position: SegmentPosition = SegmentPosition.Single,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    indicatorColor: Color? = null,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val shape = position.shape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)

    SegmentedListItem(
        selected = selected,
        onClick = { if (enabled) onClick() },
        shapes = ListItemDefaults.shapes(shape = shape, selectedShape = shape),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            selectedContainerColor = selectedColor
        ),
        modifier = modifier.then(
            if (indicatorColor != null) {
                Modifier
                    .clip(shape)
                    .drawWithContent {
                        drawContent()
                        val widthPx = 4.dp.toPx()
                        drawRect(
                            color = indicatorColor,
                            topLeft = Offset.Zero,
                            size = Size(widthPx, size.height)
                        )
                    }
            } else Modifier
        ),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        if (description != null && !expanded) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(16.dp))
                        trailing()
                    }
                }
                if (expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        content()
                    }
                }
            }
        }
    )
}
