/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.screens.search.MathResult

/** Centered loading spinner shown at the bottom of a paginated results list. */
@Composable
fun LoadMoreFooter(isLoading: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A titled section header with an optional trailing text action (e.g. "Clear all"). */
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A filter chip that shows a check icon when [selected]. */
@Composable
fun SelectableFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
        } else null,
    )
}

/** A titled row with an optional subtitle, leading icon, and trailing switch — the standard settings-sheet row. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingIcon != null) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) { leadingIcon() }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Card showing the result of a locally-evaluated math expression, with quick actions to copy or open the full calculator. */
@Composable
fun InstantMathCard(
    mathResult: MathResult,
    onCopy: (String) -> Unit,
    onOpenCalculator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = onOpenCalculator,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_calc_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_calc_local), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = "${mathResult.expression} = ${mathResult.result}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ToolzExpressiveButton(onClick = onOpenCalculator, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_calc_open))
                }
                ToolzOutlinedExpressiveButton(onClick = { onCopy(mathResult.result) }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_calc_copy))
                }
            }
        }
    }
}

private data class CategoryUi(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun categoryUi(category: SearchCategory): CategoryUi = when (category) {
    SearchCategory.ALL -> CategoryUi(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_all, Icons.Rounded.Search)
    SearchCategory.IMAGES -> CategoryUi(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_images, Icons.Rounded.Image)
    SearchCategory.NEWS -> CategoryUi(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_news, Icons.Rounded.Newspaper)
    SearchCategory.VIDEOS -> CategoryUi(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_videos, Icons.Rounded.OndemandVideo)
}

/** Horizontal row of category filter chips (All / Images / News / Videos). */
@Composable
fun SearchCategoryChips(
    selectedCategory: SearchCategory,
    onCategorySelected: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(SearchCategory.entries.toTypedArray()) { cat ->
            val ui = categoryUi(cat)
            ExpressiveFilterChip(
                selected = cat == selectedCategory,
                onClick = { onCategorySelected(cat) },
                label = { Text(stringResource(ui.labelRes), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(ui.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}
