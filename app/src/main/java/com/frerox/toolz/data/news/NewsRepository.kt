package com.frerox.toolz.data.news

import android.content.Context
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val newsApi: NewsApi,
    private val newsDao: NewsDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SYNC_STALE_MS = 6 * 60 * 60 * 1000L
        const val HISTORY_PAGE_SIZE = 10
        private const val PRUNE_AFTER_MS = 90L * 24 * 60 * 60 * 1000L
    }

    // Single-flight: concurrent triggers (boot, dashboard, worker, manual)
    // share one sync instead of racing Retrofit + Room + DataStore.
    private val syncMutex = Mutex()

    fun currentVersion(): String {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) { "0.0.0" }
    }

    suspend fun syncIfStale(force: Boolean = false): Boolean {
        return try {
            syncMutex.withLock {
                val last = settingsRepository.newsLastSync.first()
                if (!force && System.currentTimeMillis() - last < SYNC_STALE_MS) return@withLock true
                val av = currentVersion()
                val feed = newsApi.getNews(av)
                // Degraded feed (Redis outage, bad env): touch NOTHING. The
                // payload is empty by design, and treating it as truth would
                // archive the entire local cache and stall retry for 6 h.
                if (feed.degraded) return@withLock false
                val now = System.currentTimeMillis()
                newsDao.upsertAll(feed.news.map { it.toEntity(now) })
                // Deleted upstream: hard-delete cached copies everywhere
                // (popup, notifications, history) before reconciling the rest.
                if (feed.removedIds.isNotEmpty()) {
                    newsDao.deleteByIds(feed.removedIds.take(200))
                }
                reconcileRemovals(feedIds = feed.news.map { it.id }.toSet(), appVersion = av, now = now)
                newsDao.prune(now, now - PRUNE_AFTER_MS)
                settingsRepository.setNewsLastSync(now)
                if (feed.feedVersion >= 0) settingsRepository.setNewsFeedVersion(feed.feedVersion)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Change-driven sync: compares the cheap `news:version` generation counter
     * and force-syncs only when the server moved. Falls back to the stale gate
     * when the version check fails. This is what makes deletes and edits land
     * within minutes instead of waiting out the 6-hour window.
     */
    suspend fun syncIfChanged(): Boolean {
        return try {
            val remote = runCatching { newsApi.getNewsVersion() }.getOrNull()
            if (remote != null && !remote.degraded && remote.v >= 0) {
                val local = settingsRepository.newsFeedVersion.first()
                if (remote.v != local) return syncIfStale(force = true)
                return true
            }
            syncIfStale()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sync (single-flight, stale-gated) then immediately notify up to 3
     * newly-eligible items. Each id notifies exactly once; critical bypasses
     * the notifications toggle. Returns the number of posted notifications.
     */
    suspend fun syncAndNotify(): Int {
        return try {
            val ok = syncIfChanged()
            if (!ok) return 0
            val notifOn = settingsRepository.newsNotificationsEnabled.first()
            val notified = settingsRepository.newsNotifiedIds.first()
            var posted = 0
            for (candidate in notificationCandidates(limit = 3)) {
                if (candidate.id in notified) continue
                if (!notifOn && candidate.priority != "critical") continue
                NewsNotifier.post(context, candidate)
                settingsRepository.addNewsNotified(candidate.id)
                posted++
            }
            posted
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Unpublish/archive propagation: the feed only contains live items, so a cached
     * `published` item that is still time-valid and version-eligible for THIS device
     * but absent from the feed was removed server-side → archive it locally so it
     * stops popping up (it stays in history). Items targeting other versions are
     * never touched. Skipped for empty feeds (outage vs wipeout is
     * indistinguishable; true deletes arrive as tombstones) and for possibly
     * truncated feeds (100-id fetch cap).
     */
    private suspend fun reconcileRemovals(feedIds: Set<String>, appVersion: String, now: Long) {
        try {
            // An empty feed is indistinguishable from a failed fetch, so it
            // must never trigger mass-archival (real deletes arrive as
            // tombstones via removedIds). Same for a possibly-truncated feed.
            if (feedIds.isEmpty() || feedIds.size >= 100) return
            val stale = newsDao.publishedOrdered().filter { cached ->
                cached.id !in feedIds &&
                    timeValid(cached, now) &&
                    NewsVersion.isEligible(
                        appVersion,
                        cached.minAppVersion,
                        cached.maxAppVersion,
                        csv(cached.onlyVersionsCsv),
                        csv(cached.excludedVersionsCsv)
                    )
            }.map { it.id }
            if (stale.isNotEmpty()) newsDao.markArchived(stale)
        } catch (_: Exception) { /* never break sync on reconciliation */ }
    }

    suspend fun historyPage(page: Int): List<NewsEntity> {
        return try {
            newsDao.page(HISTORY_PAGE_SIZE, page * HISTORY_PAGE_SIZE)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun historyCount(): Int = try { newsDao.count() } catch (_: Exception) { 0 }

    suspend fun unreadCount(): Int {
        return try {
            val seen = settingsRepository.newsSeenIds.first()
            val all = newsDao.publishedOrdered()
            all.count { it.id !in seen }
        } catch (_: Exception) { 0 }
    }

    suspend fun markSeen(ids: Collection<String>) {
        try {
            val seen = settingsRepository.newsSeenIds.first().toMutableSet()
            seen.addAll(ids)
            settingsRepository.setNewsSeenIds(seen)
        } catch (_: Exception) { /* ignore */ }
    }

    suspend fun popupCandidate(): NewsEntity? {
        return try {
            eligibleItems().firstOrNull()
        } catch (_: Exception) { null }
    }

    /**
     * Top-N notifiable items: popup-eligible AND `notify == true`.
     * Sorted critical → pinned → newest, same as the popup queue.
     * (`notify` gates system notifications only — popups ignore it.)
     */
    suspend fun notificationCandidates(limit: Int): List<NewsEntity> {
        return try {
            eligibleItems().filter { it.notify }.take(limit.coerceIn(1, 5))
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun eligibleItems(): List<NewsEntity> {
        val now = System.currentTimeMillis()
        val av = currentVersion()
        val masterOn = settingsRepository.newsEnabled.first()
        val dismissed = settingsRepository.newsDismissedIds.first()
        val impressions = parseLongMap(settingsRepository.newsImpressionsJson.first())
        val lastShown = parseLongMap(settingsRepository.newsLastShownJson.first())
        val snoozed = parseLongMap(settingsRepository.newsSnoozedJson.first())
        val items = newsDao.publishedOrdered()
        return items
            .filter { timeValid(it, now) }
            .filter { NewsVersion.isEligible(av, it.minAppVersion, it.maxAppVersion, csv(it.onlyVersionsCsv), csv(it.excludedVersionsCsv)) }
            .filter { masterOn || it.priority == "critical" }
            .filter { it.id !in dismissed }
            .filter { (snoozed[it.id] ?: 0L) <= now }
            .filter {
                val max = it.maxImpressions
                (impressions[it.id] ?: 0L) < (max?.toLong() ?: if (it.frequency == "once") 1L else Long.MAX_VALUE)
            }
            .filter {
                val last = lastShown[it.id] ?: 0L
                when (it.frequency) {
                    "once" -> (impressions[it.id] ?: 0L) == 0L
                    "every_launch" -> true
                    "daily" -> now - last >= 24 * 60 * 60 * 1000L
                    "weekly" -> now - last >= 7 * 24 * 60 * 60 * 1000L
                    "interval" -> now - last >= (it.intervalHours ?: 72) * 60 * 60 * 1000L
                    else -> true
                }
            }
            .sortedWith(compareByDescending<NewsEntity> { it.priority == "critical" }.thenByDescending { it.pinned }.thenByDescending { it.publishAt })
    }

    private fun timeValid(e: NewsEntity, now: Long): Boolean {
        if (e.status != "published") return false
        if (now < e.publishAt) return false
        if (e.expiresAt != null && now >= e.expiresAt) return false
        return true
    }

    suspend fun markShown(id: String) {
        try {
            val imp = parseLongMap(settingsRepository.newsImpressionsJson.first()).toMutableMap()
            imp[id] = (imp[id] ?: 0L) + 1
            settingsRepository.setNewsImpressionsJson(JSONObject(imp as Map<*, *>).toString())
            val last = parseLongMap(settingsRepository.newsLastShownJson.first()).toMutableMap()
            last[id] = System.currentTimeMillis()
            settingsRepository.setNewsLastShownJson(JSONObject(last as Map<*, *>).toString())
        } catch (_: Exception) { /* ignore */ }
    }

    suspend fun dismiss(id: String) {
        try { settingsRepository.addNewsDismissed(id) } catch (_: Exception) { /* ignore */ }
    }

    suspend fun snooze24h(id: String) {
        try {
            val map = parseLongMap(settingsRepository.newsSnoozedJson.first()).toMutableMap()
            map[id] = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            settingsRepository.setNewsSnoozedJson(JSONObject(map as Map<*, *>).toString())
        } catch (_: Exception) { /* ignore */ }
    }

    private fun csv(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseLongMap(json: String): Map<String, Long> {
        return try {
            val o = JSONObject(json)
            buildMap {
                for (k in o.keys()) put(k, o.optLong(k, 0L))
            }
        } catch (_: Exception) { emptyMap() }
    }
}
