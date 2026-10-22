/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.news.NewsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEV-SPOOF: RAM-only Toolz news backlog. Powers the news screen (history,
 * search, priorities), the dashboard popup and the unread counter while the
 * sandbox is active — no feed fetch, no Room, no real notifications.
 */
object FakeNewsSandbox {

    private val items = MutableStateFlow<List<NewsEntity>>(emptyList())

    /** DEV-SPOOF: seen/tracking lives in RAM only. */
    val seen = MutableStateFlow<Set<String>>(emptySet())

    fun all(): List<NewsEntity> = items.value

    fun unreadCount(): Int =
        items.value.count { it.id !in seen.value }

    fun markSeen(ids: Collection<String>) {
        seen.value = seen.value + ids
    }

    fun reset() {
        items.value = emptyList()
        seen.value = emptySet()
    }

    /** DEV-SPOOF: seeds a believable editorial backlog whenever the sandbox activates. */
    fun arm() {
        seen.value = emptySet()
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val day = 24 * hour

        fun item(
            id: String, title: String, body: String, priority: String,
            pinned: Boolean = false, ageMs: Long, actionLabel: String? = null,
            actionUrl: String? = null, status: String = "active",
        ) = NewsEntity(
            id = id, title = title, body = body, imageUrl = null,
            actionLabel = actionLabel, actionUrl = actionUrl,
            priority = priority, status = status, pinned = pinned,
            publishAt = now - ageMs, expiresAt = null,
            minAppVersion = null, maxAppVersion = null,
            onlyVersionsCsv = "", excludedVersionsCsv = "",
            delaySeconds = 0, frequency = "once", intervalHours = null,
            maxImpressions = null, dismissible = true, showInHistory = true,
            requiresAction = actionLabel != null, notify = false,
            disappearing = false, receivedAt = now - ageMs,
        )

        items.value = listOf(
            item(
                "fixture-tip-developer-mode",
                "Developer Mode is here",
                "Every tool can now run on local fixture data — useful for showcasing. No watermarks, no real data touched. Long-press any starred dashboard card to turn its sandbox off.",
                "update", pinned = true, ageMs = 2 * hour,
                actionLabel = "Open developer options", actionUrl = "toolz://developer",
            ),
            item(
                "fixture-tip-whisper-local",
                "Whisper runs fully local",
                "The private chat sandbox now needs no accounts, no network and no push relay. Everything is simulated on-device.",
                "update", ageMs = 1 * day,
                actionLabel = "Try Whisper",
            ),
            item(
                "fixture-fix-cleaner-sandbox",
                "Cleaner got a sandbox overhaul",
                "The file cleaner sandbox now ships with fixture scan results, RAM trash and undo — it never deletes real files.",
                "release", ageMs = 2 * day,
            ),
            item(
                "fixture-event-stickers",
                "Skin of the week: Frosted Neon",
                "The community chip contest winner ships with the winter theme pack. Preview it from Settings → App skin.",
                "community", ageMs = 3 * day,
            ),
            item(
                "fixture-maint-sync",
                "Maintenance window tonight",
                "Scheduled: 02:00–03:00 local, cloud mirrors are refreshed from the editorial feed.",
                "info", ageMs = 4 * day,
            ),
            item(
                "fixture-outage-2026-01",
                "(Resolved) Brief mirror outage",
                "A regional mirror flaked on Oct 3; all regions confirmed healthy. Kept for transparency.",
                "info", ageMs = 14 * day,
            ),
            item(
                "fixture-tip-qr",
                "Scanner payloads rotate",
                "The scanner sandbox now shows a different payload every open, so quick showcases tell a richer story.",
                "release", ageMs = 16 * day,
            ),
        )
    }

    /** DEV-SPOOF: search over the RAM backlog only. */
    fun search(query: String, priority: String): List<NewsEntity> {
        val q = query.trim()
        return all().filter { item ->
            (priority == "all" || item.priority == priority) &&
                (q.isEmpty() ||
                    item.title.contains(q, ignoreCase = true) ||
                    item.body.contains(q, ignoreCase = true))
        }
    }
}
