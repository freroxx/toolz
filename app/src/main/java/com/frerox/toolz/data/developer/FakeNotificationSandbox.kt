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

import com.frerox.toolz.data.notifications.NotificationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEV-SPOOF: RAM-only notification vault fixture. A believable cross-app
 * notification backlog spread over several days so date filters, categories,
 * per-app details, delete and clear-all are all explorable — while the real
 * listener, Room vault and Shizuku listener-grant stay untouched.
 */
object FakeNotificationSandbox {

    private val entries = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val fixtureNotifications: StateFlow<List<NotificationEntry>> = entries.asStateFlow()

    private var nextId = 1L

    private val hidden = MutableStateFlow<Set<String>>(emptySet())
    val fixtureHidden: StateFlow<Set<String>> = hidden.asStateFlow()

    /** DEV-SPOOF: (re)seeds a fresh backlog whenever the sandbox activates. */
    fun arm() {
        nextId = 1L
        hidden.value = emptySet()
        val now = System.currentTimeMillis()
        val min = 60_000L
        val hour = 60 * min
        val day = 24 * hour

        entries.value = listOf(
            notif(
                "com.example.social", "Pulse Social", "Mina 🎧",
                "reacted to your story: “that rail shot is unreal”", now - 4 * min,
                "Social",
            ),
            notif(
                "com.example.chat", "WireChat", "Team Atlas · 3 new",
                "Kai: standup notes are up, fixture builds attached", now - 26 * min,
                "Work",
            ),
            notif(
                "com.example.mail", "Bullet Mail", "Atlas Bank",
                "Your January statement is ready to view", now - 2 * hour,
                "Finance",
            ),
            notif(
                "com.example.player", "Nimbus Player",
                "Download finished", "Interstellar Roadtrip — EP 4 (1080p)", now - 3 * hour,
                "General",
            ),
            notif(
                "com.example.social", "Pulse Social", "Den Group (5)",
                "Marta: we’re splitting check in 10, be there", now - 5 * hour,
                "Social",
            ),
            notif(
                "com.example.maps", "Atlas Maps", null,
                "Traffic cleared on your usual route — 8 min faster", now - 9 * hour,
                "General",
            ),
            notif(
                "com.example.chat", "WireChat", "Kai", "sent a voice note · 0:47",
                now - 13 * hour,
                "Work",
            ),
            notif(
                "com.example.mail", "Bullet Mail", "Toolz",
                "Changelog v2.6.0 — sandbox mode, minus watermark 👀", now - 1 * day,
                "General",
            ),
            notif(
                "com.example.bank", "Atlas Bank", "Card ending 4417",
                "2.40 EUR authorized — Cafe Nine", now - 1 * day - 4 * hour,
                "Finance",
            ),
            notif(
                "com.example.social", "Pulse Social", "Yuri",
                "says you look familiar… 👀", now - 2 * day + 2 * hour,
                "Social",
            ),
            notif(
                "com.example.player", "Nimbus Player", null,
                "Weekly listening wrap is ready", now - 3 * day,
                "General",
            ),
        )
    }

    private fun notif(
        pkg: String, app: String, title: String?, text: String, ts: Long, category: String,
    ) = NotificationEntry(
        id = nextId++,
        packageName = pkg,
        appName = app,
        title = title,
        text = text,
        timestamp = ts,
        category = category,
    )

    /** DEV-SPOOF: RAM delete — the vault row disappears like reality. */
    fun delete(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    fun clearAll() {
        entries.value = emptyList()
    }

    fun hideApp(packageName: String) {
        hidden.value = hidden.value + packageName
    }

    fun unhideApp(packageName: String) {
        hidden.value = hidden.value - packageName
    }

    /** DEV-SPOOF: live fixture-based distinct package list. */
    fun distinctPackages(): List<String> =
        entries.value.distinctBy { it.packageName }.map { it.packageName }

    /** DEV-SPOOF: count + last notification read from RAM. */
    fun lastForPackage(packageName: String): NotificationEntry? =
        entries.value.filter { it.packageName == packageName }.maxByOrNull { it.timestamp }

    fun countForPackage(packageName: String): Int =
        entries.value.count { it.packageName == packageName }

    fun reset() {
        entries.value = emptyList()
        hidden.value = emptySet()
        nextId = 1L
    }
}
