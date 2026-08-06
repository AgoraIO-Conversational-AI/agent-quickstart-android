package com.androidengineers.agent_quickstart_android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidengineers.agent_quickstart_android.model.JournalEntryUiModel
import com.androidengineers.agent_quickstart_android.ui.components.AgentIconControlButton
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidCoralSoft
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSageSoft
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSentenceStyle
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidShapes
import com.androidengineers.agent_quickstart_android.ui.theme.BetterSaidSpacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun JournalScreen(
    entries: List<JournalEntryUiModel>,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var visibleEntryCount by rememberSaveable { mutableStateOf(InitialVisibleJournalEntries) }
    val filteredEntries = remember(entries, query) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                listOf(
                    entry.category,
                    entry.originalText,
                    entry.correctedText,
                    entry.tags.joinToString(" "),
                ).any { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    }
    val visibleEntries = remember(filteredEntries, visibleEntryCount) {
        filteredEntries.take(visibleEntryCount)
    }
    val hasOlderEntries = visibleEntryCount < filteredEntries.size

    LaunchedEffect(query, entries.size) {
        visibleEntryCount = InitialVisibleJournalEntries
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
        contentPadding = PaddingValues(
            top = BetterSaidSpacing.Md,
            bottom = 132.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Lg),
    ) {
        item(key = "journal-header") {
            JournalHeader(
                query = query,
                onQueryChange = { query = it },
                entryCount = entries.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
            )
        }

        if (filteredEntries.isEmpty()) {
            item(key = "journal-empty") {
                JournalEmptyState(
                    hasSearchQuery = query.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                )
            }
        } else {
            visibleEntries
                .groupBy { it.groupLabel() }
                .forEach { (groupLabel, groupEntries) ->
                    item(key = "group-$groupLabel") {
                        JournalDateDivider(
                            label = groupLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                        )
                    }

                    groupEntries.forEach { entry ->
                        item(key = entry.id) {
                            JournalEntryCard(
                                entry = entry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 760.dp),
                            )
                        }
                    }
                }

            if (hasOlderEntries) {
                item(key = "older-memories") {
                    ViewOlderMemoriesButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        onClick = {
                            visibleEntryCount += JournalEntriesPageSize
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalEmptyState(
    hasSearchQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BetterSaidShapes.Md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(BetterSaidSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = if (hasSearchQuery) "No matching entries" else "Your journal is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (hasSearchQuery) {
                    "Try another word or clear the search box."
                } else {
                    "Saved corrections will appear here after you add them."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JournalHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    entryCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Journal",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = "$entryCount entries total",
                style = BetterSaidSentenceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        JournalSearchField(
            query = query,
            onQueryChange = onQueryChange,
        )
    }
}

@Composable
private fun JournalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBottomInkLine(
                if (isFocused) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            .padding(
                start = BetterSaidSpacing.Md,
                top = BetterSaidSpacing.Md,
                end = BetterSaidSpacing.Md,
                bottom = BetterSaidSpacing.Md,
            ),
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            interactionSource = interactionSource,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) {
                        Text(
                            text = "Search your thoughts...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun JournalDateDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryUiModel,
    modifier: Modifier = Modifier,
) {
    val categoryStyle = entry.categoryStyle()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val lift by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(),
        label = "journal-card-lift",
    )
    val shadowOffset by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 4.dp,
        animationSpec = spring(),
        label = "journal-card-shadow",
    )

    Box(modifier = modifier.padding(end = 6.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(RoundedCornerShape(BetterSaidShapes.Sm))
                .background(Color.Black),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = lift, y = lift)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(BetterSaidShapes.Sm),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 0.dp,
        ) {
            Box {
                Icon(
                    imageVector = categoryStyle.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(BetterSaidSpacing.Sm)
                        .size(82.dp),
                )
                Column(
                    modifier = Modifier.padding(BetterSaidSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
                ) {
                    JournalEntryMetaRow(
                        entry = entry,
                        categoryStyle = categoryStyle,
                    )
                    JournalCorrectionPair(entry = entry)
                    JournalTags(tags = entry.tags)
                }
            }
        }
    }
}

@Composable
private fun JournalEntryMetaRow(
    entry: JournalEntryUiModel,
    categoryStyle: JournalCategoryStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = categoryStyle.containerColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = BetterSaidSpacing.Sm, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoryStyle.icon,
                    contentDescription = null,
                    tint = categoryStyle.contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelLarge,
                    color = categoryStyle.contentColor,
                )
            }
        }
        Text(
            text = entry.timeLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun JournalCorrectionPair(entry: JournalEntryUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "I SAID:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "BETTER VERSION:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "\"${entry.correctedText}\"",
                style = BetterSaidSentenceStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun JournalTags(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Xs),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(BetterSaidShapes.Sm),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            ) {
                Text(
                    text = tag.uppercase(Locale.ROOT),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ViewOlderMemoriesButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(BetterSaidShapes.Lg),
        color = Color.Transparent,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BetterSaidSpacing.Md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "View Older Memories",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(BetterSaidSpacing.Sm))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsPlaceholderScreen(
    onToggleTheme: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .betterSaidPaperPattern(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(top = BetterSaidSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Lg),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BetterSaidSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(BetterSaidShapes.Md),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(BetterSaidSpacing.Md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Switch between light and dark paper.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AgentIconControlButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "Toggle theme",
                        active = false,
                        onClick = onToggleTheme,
                    )
                }
            }
        }
    }
}

private fun Modifier.drawBottomInkLine(color: Color): Modifier {
    return drawBehind {
        val strokeWidth = 2.dp.toPx()
        val y = size.height - strokeWidth / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
    }
}

private data class JournalCategoryStyle(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

private fun JournalEntryUiModel.categoryStyle(): JournalCategoryStyle {
    return when (category.lowercase(Locale.ROOT)) {
        "daily life" -> JournalCategoryStyle(
            icon = Icons.Outlined.Eco,
            containerColor = BetterSaidSageSoft,
            contentColor = Color(0xFF4E6952),
        )

        "school" -> JournalCategoryStyle(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            containerColor = BetterSaidCoralSoft,
            contentColor = Color(0xFF400102),
        )

        else -> JournalCategoryStyle(
            icon = Icons.Outlined.ChatBubbleOutline,
            containerColor = Color(0xFFE4E2DD),
            contentColor = Color(0xFF1B1C19),
        )
    }
}

private fun JournalEntryUiModel.groupLabel(): String {
    val entryCalendar = Calendar.getInstance().apply { timeInMillis = spokenAtMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

    return when {
        entryCalendar.isSameDay(today) -> "Today"
        entryCalendar.isSameDay(yesterday) -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(spokenAtMillis)
    }
}

private fun JournalEntryUiModel.timeLabel(): String {
    return if (groupLabel() == "Today") {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(spokenAtMillis)
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(spokenAtMillis)
    }
}

private fun Calendar.isSameDay(other: Calendar): Boolean {
    return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private const val InitialVisibleJournalEntries = 8
private const val JournalEntriesPageSize = 8
