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

package com.frerox.toolz.data.calendar

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calendarSyncDataStore by preferencesDataStore("calendar_sync_settings")

/**
 * DataStore-backed settings repository for calendar sync preferences.
 *
 * Stores CalDAV account credentials (including passwords) in DataStore.
 * For production hardening, passwords should ideally be stored in the
 * Android Keystore — filed as a future improvement (CAL-SEC-01).
 *
 * Uses only androidx.datastore and org.json (both bundled with the Android SDK).
 * Zero proprietary dependencies — fully FOSS/F-Droid compatible.
 */
@Singleton
class CalendarSyncSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CalSyncSettings"

        // ── DataStore keys ──────────────────────────────────────────────────
        private val KEY_AUTO_SYNC_ENABLED  = booleanPreferencesKey("cal_auto_sync_enabled")
        private val KEY_AUTO_SYNC_INTERVAL = intPreferencesKey("cal_auto_sync_interval_hours")
        private val KEY_FIRST_DAY_OF_WEEK  = intPreferencesKey("cal_first_day_of_week")
        private val KEY_SHOW_WEEK_NUMBERS  = booleanPreferencesKey("cal_show_week_numbers")
        private val KEY_CALDAV_ACCOUNTS    = stringPreferencesKey("cal_caldav_accounts_json")
        private val KEY_PHONE_CAL_IDS      = stringPreferencesKey("cal_phone_calendar_ids")
        private val KEY_LAST_SYNC_MS       = stringPreferencesKey("cal_last_sync_ms")
        private val KEY_USER_CALENDARS     = stringPreferencesKey("cal_user_calendars_json")

        val DEFAULT_CALENDARS = listOf(
            UserCalendar("default", "Personal", "#3F51B5"),
            UserCalendar("work", "Work", "#009688"),
            UserCalendar("academic", "Academic", "#FF9800")
        )

        // ── Defaults ────────────────────────────────────────────────────────
        const val DEFAULT_INTERVAL_HOURS   = 6
    }

    private val ds get() = context.calendarSyncDataStore

    // ── Flows ───────────────────────────────────────────────────────────────

    val autoSyncEnabled: Flow<Boolean> = ds.data.map { it[KEY_AUTO_SYNC_ENABLED] ?: false }
    val autoSyncIntervalHours: Flow<Int> = ds.data.map { it[KEY_AUTO_SYNC_INTERVAL] ?: DEFAULT_INTERVAL_HOURS }
    val firstDayOfWeek: Flow<Int> = ds.data.map { it[KEY_FIRST_DAY_OF_WEEK] ?: Calendar.MONDAY }
    val showWeekNumbers: Flow<Boolean> = ds.data.map { it[KEY_SHOW_WEEK_NUMBERS] ?: false }
    val calDavAccounts: Flow<List<CalDavSyncClient.CalDavAccount>> = ds.data.map { prefs ->
        parseAccounts(prefs[KEY_CALDAV_ACCOUNTS])
    }
    val selectedPhoneCalendarIds: Flow<Set<Long>> = ds.data.map { prefs ->
        prefs[KEY_PHONE_CAL_IDS]?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet() ?: emptySet()
    }
    val lastSyncMs: Flow<Long> = ds.data.map {
        it[KEY_LAST_SYNC_MS]?.toLongOrNull() ?: 0L
    }
    val userCalendars: Flow<List<UserCalendar>> = ds.data.map { prefs ->
        parseUserCalendars(prefs[KEY_USER_CALENDARS])
    }

    // ── Setters ─────────────────────────────────────────────────────────────

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        ds.edit { it[KEY_AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setAutoSyncIntervalHours(hours: Int) {
        ds.edit { it[KEY_AUTO_SYNC_INTERVAL] = hours.coerceIn(0, 168) }
    }

    suspend fun setFirstDayOfWeek(day: Int) {
        ds.edit { it[KEY_FIRST_DAY_OF_WEEK] = day }
    }

    suspend fun setShowWeekNumbers(show: Boolean) {
        ds.edit { it[KEY_SHOW_WEEK_NUMBERS] = show }
    }

    suspend fun addCalDavAccount(account: CalDavSyncClient.CalDavAccount) {
        ds.edit { prefs ->
            val current = parseAccounts(prefs[KEY_CALDAV_ACCOUNTS]).toMutableList()
            // Replace if same id, otherwise append
            val idx = current.indexOfFirst { it.id == account.id }
            if (idx >= 0) current[idx] = account else current.add(account)
            prefs[KEY_CALDAV_ACCOUNTS] = serializeAccounts(current)
        }
    }

    suspend fun removeCalDavAccount(accountId: String) {
        ds.edit { prefs ->
            val current = parseAccounts(prefs[KEY_CALDAV_ACCOUNTS])
                .filter { it.id != accountId }
            prefs[KEY_CALDAV_ACCOUNTS] = serializeAccounts(current)
        }
    }

    suspend fun setSelectedPhoneCalendarIds(ids: Set<Long>) {
        ds.edit { it[KEY_PHONE_CAL_IDS] = ids.joinToString(",") }
    }

    suspend fun updateLastSyncMs(ms: Long = System.currentTimeMillis()) {
        ds.edit { it[KEY_LAST_SYNC_MS] = ms.toString() }
    }

    // ── One-shot reads (for workers) ─────────────────────────────────────────

    suspend fun getCalDavAccountsSnapshot(): List<CalDavSyncClient.CalDavAccount> =
        ds.data.map { parseAccounts(it[KEY_CALDAV_ACCOUNTS]) }.first()

    suspend fun getSelectedPhoneCalendarIdsSnapshot(): Set<Long> =
        ds.data.map { prefs ->
            prefs[KEY_PHONE_CAL_IDS]?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()
        }.first()

    suspend fun addUserCalendar(calendar: UserCalendar) {
        ds.edit { prefs ->
            val current = parseUserCalendars(prefs[KEY_USER_CALENDARS]).toMutableList()
            val idx = current.indexOfFirst { it.id == calendar.id }
            if (idx >= 0) current[idx] = calendar else current.add(calendar)
            prefs[KEY_USER_CALENDARS] = serializeUserCalendars(current)
        }
    }

    suspend fun removeUserCalendar(calendarId: String) {
        ds.edit { prefs ->
            val current = parseUserCalendars(prefs[KEY_USER_CALENDARS]).filter { it.id != calendarId }
            prefs[KEY_USER_CALENDARS] = serializeUserCalendars(current)
        }
    }

    // ── JSON serialization (uses Android SDK's org.json — zero extra deps) ──

    private fun serializeUserCalendars(calendars: List<UserCalendar>): String {
        return runCatching {
            JSONArray().also { arr ->
                for (cal in calendars) {
                    arr.put(JSONObject().apply {
                        put("id", cal.id)
                        put("name", cal.name)
                        put("color", cal.color)
                    })
                }
            }.toString()
        }.getOrElse { "[]" }
    }

    private fun parseUserCalendars(json: String?): List<UserCalendar> {
        if (json.isNullOrBlank() || json == "[]") return DEFAULT_CALENDARS
        return runCatching {
            val arr = JSONArray(json)
            val list = mutableListOf<UserCalendar>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UserCalendar(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        color = obj.optString("color", "#3F51B5")
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_CALENDARS else list
        }.getOrElse { DEFAULT_CALENDARS }
    }

    private fun serializeAccounts(accounts: List<CalDavSyncClient.CalDavAccount>): String {
        return runCatching {
            JSONArray().also { arr ->
                for (acc in accounts) {
                    arr.put(JSONObject().apply {
                        put("id", acc.id)
                        put("url", acc.url)
                        put("username", acc.username)
                        put("password", acc.password)
                        put("displayName", acc.displayName)
                    })
                }
            }.toString()
        }.getOrElse {
            Log.e(TAG, "serializeAccounts failed", it)
            "[]"
        }
    }

    private fun parseAccounts(json: String?): List<CalDavSyncClient.CalDavAccount> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CalDavSyncClient.CalDavAccount(
                    id          = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    url         = obj.getString("url"),
                    username    = obj.getString("username"),
                    password    = obj.getString("password"),
                    displayName = obj.optString("displayName", ""),
                )
            }
        }.getOrElse {
            Log.e(TAG, "parseAccounts failed", it)
            emptyList()
        }
    }
}

data class UserCalendar(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#3F51B5"
)
