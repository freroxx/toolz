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

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal CalDAV client built on OkHttp (Apache 2.0 — FOSS compatible).
 *
 * CalDAV is an IETF open standard (RFC 4791) for calendar synchronisation.
 * Compatible servers include: Nextcloud, Baikal, Radicale, DAViCal,
 * FastMail, Proton Calendar (CalDAV bridge), Apple iCloud (CalDAV),
 * Google Calendar (CalDAV endpoint), Horde Groupware, SOGo, EteSync, etc.
 *
 * Auth: HTTP Basic (RFC 7617) — username + password Base64 encoded.
 * No OAuth, no proprietary SDKs, no Google Play Services required.
 */
@Singleton
class CalDavSyncClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "CalDavSyncClient"
        private val XML_MEDIA  = "application/xml; charset=utf-8".toMediaType()
        private val TEXT_MEDIA = "text/calendar; charset=utf-8".toMediaType()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data types
    // ─────────────────────────────────────────────────────────────────────────

    data class CalDavAccount(
        val url: String,      // e.g. https://cloud.example.com/remote.php/dav/calendars/user/personal/
        val username: String,
        val password: String, // stored encrypted via CalendarSyncSettingsRepository
        val displayName: String = "",
        val id: String = UUID.randomUUID().toString(),
    )

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        data class Failure(val error: String) : SyncResult()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Connection test (PROPFIND depth 0)
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the display-name of the calendar collection, or an error string. */
    suspend fun testConnection(account: CalDavAccount): SyncResult = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop>
                <D:displayname/>
                <C:calendar-description/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA)

        val request = Request.Builder()
            .url(account.url.ensureTrailingSlash())
            .method("PROPFIND", body)
            .header("Depth", "0")
            .header("Authorization", account.basicAuthHeader())
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()

        runCatching {
            val client = okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 207) {
                    val xml = resp.body?.string() ?: ""
                    val name = extractXmlValue(xml, "displayname").ifBlank { account.url }
                    SyncResult.Success("Connected: $name")
                } else {
                    SyncResult.Failure("HTTP ${resp.code}: ${resp.message}")
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "testConnection failed", e)
            SyncResult.Failure(e.message ?: "Unknown error")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fetch events (REPORT calendar-query)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch all VEVENT components from the CalDAV collection.
     * Returns parsed [EventEntry] list ready for Room insert.
     */
    suspend fun fetchEvents(account: CalDavAccount): kotlin.Result<List<EventEntry>> = withContext(Dispatchers.IO) {
        val reportBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop>
                <D:getetag/>
                <C:calendar-data/>
              </D:prop>
              <C:filter>
                <C:comp-filter name="VCALENDAR">
                  <C:comp-filter name="VEVENT"/>
                </C:comp-filter>
              </C:filter>
            </C:calendar-query>
        """.trimIndent().toRequestBody(XML_MEDIA)

        val request = Request.Builder()
            .url(account.url.ensureTrailingSlash())
            .method("REPORT", reportBody)
            .header("Depth", "1")
            .header("Authorization", account.basicAuthHeader())
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()

        runCatching {
            val client = okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 207) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
                val xml = resp.body?.string() ?: ""
                // Extract all calendar-data blocks from the multistatus response
                val calDataBlocks = extractCalendarDataBlocks(xml)
                val events = mutableListOf<EventEntry>()
                for (block in calDataBlocks) {
                    events.addAll(IcsParser.parse(block))
                }
                Log.d(TAG, "Fetched ${events.size} events from ${account.url}")
                events
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Push a single event (PUT)
    // ─────────────────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Push a single [EventEntry] to the CalDAV collection as a VEVENT.
     * Uses a deterministic UID based on event id + timestamp.
     */
    suspend fun pushEvent(account: CalDavAccount, event: EventEntry): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        val uid = "toolz-${event.id}-${event.timestamp}"
        val icsContent = IcsParser.serialize(listOf(event))
        val resourceUrl = "${account.url.ensureTrailingSlash()}$uid.ics"

        val request = Request.Builder()
            .url(resourceUrl)
            .put(icsContent.toRequestBody(TEXT_MEDIA))
            .header("Authorization", account.basicAuthHeader())
            .header("Content-Type", "text/calendar; charset=utf-8")
            .build()

        runCatching {
            val client = okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("PUT failed HTTP ${resp.code}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun CalDavAccount.basicAuthHeader(): String {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"

    /** Naive XML value extractor — no DOM dependency needed for simple tags */
    private fun extractXmlValue(xml: String, tagName: String): String {
        val pattern = Regex("<[^>]*:?$tagName[^>]*>(.*?)<[^>]*:?$tagName>", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    /**
     * Extract raw VCALENDAR/VEVENT blocks from a CalDAV multistatus XML response.
     * Looks for <cal:calendar-data> or <C:calendar-data> elements.
     */
    private fun extractCalendarDataBlocks(xml: String): List<String> {
        val blocks = mutableListOf<String>()
        // Match calendar-data content (may be wrapped in CDATA or raw)
        val pattern = Regex(
            "<[^>]*:?calendar-data[^>]*>\\s*(<\\?[^>]*\\?>)?\\s*(BEGIN:VCALENDAR.*?END:VCALENDAR)",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in pattern.findAll(xml)) {
            match.groupValues.getOrNull(2)?.let {
                blocks.add(it.trim())
            }
        }
        return blocks
    }
}
