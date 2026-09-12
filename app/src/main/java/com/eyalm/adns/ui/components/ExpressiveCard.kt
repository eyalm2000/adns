package com.eyalm.adns.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun ExpressiveCardHeader(
    title: String,
    description: String? = null,
    maturity: FeatureMaturity = FeatureMaturity.STABLE,
    isBeta: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val effectiveMaturity = if (maturity != FeatureMaturity.STABLE) maturity else if (isBeta) FeatureMaturity.BETA else FeatureMaturity.STABLE
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (effectiveMaturity != FeatureMaturity.STABLE) {
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
                    else -> {""}
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (badge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    badge()
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}
