package com.frerox.toolz.ui.screens.news

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.ui.theme.ToolzTheme

private fun fakeNews(
    id: String = "preview1",
    title: String = "What's new in 1.2",
    body: String = "**Faster** downloads, a new cleaner engine and **offline mode** fixes.\n\n- Speed up to 2x\n- [Read the changelog](https://toolz-app.vercel.app)",
    priority: String = "feature",
    imageUrl: String? = null,
    actionLabel: String? = "Try it",
    actionUrl: String? = "https://toolz-app.vercel.app",
    pinned: Boolean = true,
    requiresAction: Boolean = false,
    delaySeconds: Int = 5
) = NewsEntity(
    id = id,
    title = title,
    body = body,
    imageUrl = imageUrl,
    actionLabel = actionLabel,
    actionUrl = actionUrl,
    priority = priority,
    status = "published",
    pinned = pinned,
    publishAt = System.currentTimeMillis(),
    expiresAt = null,
    minAppVersion = null,
    maxAppVersion = null,
    onlyVersionsCsv = "",
    excludedVersionsCsv = "",
    delaySeconds = delaySeconds,
    frequency = "once",
    intervalHours = null,
    maxImpressions = 3,
    dismissible = true,
    showInHistory = true,
    requiresAction = requiresAction,
    notify = true,
    receivedAt = System.currentTimeMillis()
)

@Preview(name = "Popup standard", showBackground = true)
@Composable
fun NewsPopupStandardPreview() {
    ToolzTheme {
        Column(Modifier.padding(16.dp)) {
            NewsHistoryCard(item = fakeNews(), highlighted = false, seen = false, onAction = {})
        }
    }
}

@Preview(name = "Popup dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun NewsPopupDarkPreview() {
    ToolzTheme {
        Column(Modifier.padding(16.dp)) {
            NewsHistoryCard(item = fakeNews(title = "Critical security fix", priority = "critical", pinned = false), highlighted = true, seen = false, onAction = {})
        }
    }
}

@Preview(name = "Card text-only", showBackground = true)
@Composable
fun NewsCardTextOnlyPreview() {
    ToolzTheme {
        Column(Modifier.padding(16.dp)) {
            NewsHistoryCard(item = fakeNews(actionLabel = null, actionUrl = null, pinned = false), highlighted = false, seen = true, onAction = {})
        }
    }
}

@Preview(name = "Card critical blocking", showBackground = true)
@Composable
fun NewsCardCriticalPreview() {
    ToolzTheme {
        Column(Modifier.padding(16.dp)) {
            NewsHistoryCard(
                item = fakeNews(
                    id = "crit1",
                    title = "Service outage resolved",
                    body = "Downloads are back to normal. Thanks for your patience.",
                    priority = "critical",
                    requiresAction = true
                ),
                highlighted = true,
                seen = false,
                onAction = {}
            )
        }
    }
}

private const val LONG_BODY = "## Faster downloads\n\n**2x speed** on all networks, plus a rebuilt cleaner engine.\n\n- Speed up to 2x\n- [Read the changelog](https://toolz-app.vercel.app)\n\n> Rolling out gradually over the next week.\n\n```\nSettings → Toolz News → Check for news\n```\n\n| Plan | Speed |\n| --- | --- |\n| Free | 2x |\n| Pro | 5x |"

@Preview(name = "Popup standard", showBackground = true)
@Composable
fun ToolzNewsPopupStandardPreview() {
    ToolzTheme {
        ToolzNewsPopup(
            item = fakeNews(),
            masterNewsEnabled = true,
            onAction = {},
            onLater = {},
            onDismiss = {},
            onViewAll = {}
        )
    }
}

@Preview(name = "Popup long markdown", showBackground = true)
@Composable
fun ToolzNewsPopupLongPreview() {
    ToolzTheme {
        ToolzNewsPopup(
            item = fakeNews(id = "long1", title = "Everything new in 1.2", body = LONG_BODY),
            masterNewsEnabled = true,
            onAction = {},
            onLater = {},
            onDismiss = {},
            onViewAll = {}
        )
    }
}

@Preview(name = "Popup critical blocking", showBackground = true)
@Composable
fun ToolzNewsPopupBlockingPreview() {
    ToolzTheme {
        ToolzNewsPopup(
            item = fakeNews(
                id = "crit2",
                title = "Security update required",
                body = "Please update now. **Critical** fix inside.",
                priority = "critical",
                pinned = false,
                requiresAction = true
            ),
            masterNewsEnabled = false,
            onAction = {},
            onLater = {},
            onDismiss = {},
            onViewAll = {}
        )
    }
}

@Preview(name = "Popup dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ToolzNewsPopupDarkPreview() {
    ToolzTheme {
        ToolzNewsPopup(
            item = fakeNews(),
            masterNewsEnabled = true,
            onAction = {},
            onLater = {},
            onDismiss = {},
            onViewAll = {}
        )
    }
}
