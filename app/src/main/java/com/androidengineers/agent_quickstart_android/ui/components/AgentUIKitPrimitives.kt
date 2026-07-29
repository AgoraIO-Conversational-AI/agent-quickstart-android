package com.androidengineers.agent_quickstart_android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing

/**
 * A small subset of primitives adapted from the public agent-uikit Android repo
 * for BetterSaid's paper-and-ink screen design.
 */

enum class AgentButtonVariant {
    Primary,
    Secondary,
    Destructive,
}

@Composable
fun AgentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(BetterSaidShapes.Md),
        border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BetterSaidSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
        ) {
            if (title != null || subtitle != null) {
                Surface(
                    shape = RoundedCornerShape(BetterSaidShapes.Md),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = BetterSaidSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
                    ) {
                        title?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun AgentButton(
    text: String,
    modifier: Modifier = Modifier,
    variant: AgentButtonVariant = AgentButtonVariant.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    when (variant) {
        AgentButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = RoundedCornerShape(BetterSaidShapes.Md),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = BetterSaidSpacing.Md),
                border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.primary),
            ) {
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        }

        AgentButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = RoundedCornerShape(BetterSaidShapes.Md),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = BetterSaidSpacing.Md),
                border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        }

        AgentButtonVariant.Destructive -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = RoundedCornerShape(BetterSaidShapes.Md),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = BetterSaidSpacing.Md),
                border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                ),
            ) {
                Text(text = text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    FilterChip(
        selected = highlighted,
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (highlighted) accentColor else MaterialTheme.colorScheme.outlineVariant)
            )
        },
        border = BorderStroke(
            BetterSaidShapes.InkStroke,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = FilterChipDefaults.filterChipColors(
            disabledContainerColor = if (highlighted) {
                accentColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
            },
            disabledLabelColor = if (highlighted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            disabledLeadingIconColor = accentColor,
            selectedContainerColor = accentColor.copy(alpha = 0.08f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun AgentIconControlButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val activeContainer = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val activeContent = if (destructive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    if (active) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = activeContainer,
                contentColor = activeContent,
            ),
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
            OutlinedIconButton(
                onClick = onClick,
                modifier = modifier.size(56.dp),
            border = BorderStroke(BetterSaidShapes.InkStroke, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AgentAvatarBadge(
    name: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    val initials = name
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(highlightColor.copy(alpha = 0.12f))
            .border(
                width = BetterSaidShapes.InkStroke,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifBlank { "AI" },
            style = MaterialTheme.typography.titleMedium,
            color = highlightColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun InfoField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(BetterSaidShapes.Lg))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
fun LabeledIconText(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BetterSaidShapes.Lg))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
