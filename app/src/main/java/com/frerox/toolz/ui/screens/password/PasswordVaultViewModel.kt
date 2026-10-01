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

package com.frerox.toolz.ui.screens.password

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.util.password.CsvEngine
import com.frerox.toolz.util.password.GeneratorSpecStore
import com.frerox.toolz.util.password.PasswordGenerator
import com.frerox.toolz.util.password.PasswordUtils
import com.frerox.toolz.util.password.PwnedCheck
import com.frerox.toolz.util.password.VaultPasswordEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordVaultViewModel @Inject constructor(
    private val passwordDao: PasswordDao,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val scanProgress = _scanProgress.asStateFlow()

    private val _importMessage = MutableStateFlow<ImportMessage?>(null)
    val importMessage = _importMessage.asStateFlow()

    private val _generatorSettings = MutableStateFlow(
        settingsFromSpec(GeneratorSpecStore.load(appContext))
    )
    val generatorSettings = _generatorSettings.asStateFlow()

    private var scanJob: Job? = null
    private var autoScanDone = false

    data class GeneratorSettings(
        val length: Float = 16f,
        val includeLowercase: Boolean = true,
        val includeSymbols: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeUppercase: Boolean = true,
        val excludeAmbiguous: Boolean = false,
        val customSymbols: String = "",
        val pinMode: Boolean = false
    )

    data class VaultStats(
        val total: Int = 0,
        val breached: Int = 0,
        val weak: Int = 0,
        val averageStrength: Float = 0f,
        val tierCounts: List<Int> = listOf(0, 0, 0, 0, 0)
    )

    data class ImportMessage(
        val imported: Int,
        val skipped: Int,
        val truncated: Boolean
    )

    // Single source: one DAO subscription shared by list + stats + categories.
    private val allPasswords: StateFlow<List<PasswordEntity>> =
        passwordDao.getAllPasswords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passwords: StateFlow<List<PasswordEntity>> = combine(
        _searchQuery.debounce(250),
        allPasswords
    ) { query, list ->
        val q = query.trim()
        if (q.isBlank()) {
            list.sortedWith(
                compareByDescending<PasswordEntity> { it.isComplete }
                    .thenBy { it.name.lowercase() }
            )
        } else {
            list.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.username.contains(q, ignoreCase = true) ||
                    (it.url?.contains(q, ignoreCase = true) == true) ||
                    (PasswordUtils.normalizeHost(it.url)?.contains(q.lowercase()) == true) ||
                    (it.password.isEmpty() && "no password".contains(q, ignoreCase = true))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultStats: StateFlow<VaultStats> = allPasswords.map { list ->
        if (list.isEmpty()) VaultStats()
        else {
            val tiers = IntArray(5)
            list.filter { it.password.isNotEmpty() }.forEach {
                tiers[it.strength.coerceIn(0, 4)]++
            }
            VaultStats(
                total = list.size,
                breached = list.count { (it.pwnedCount ?: 0) > 0 },
                weak = list.count { it.password.isNotEmpty() && it.strength < 2 },
                averageStrength = list.filter { it.password.isNotEmpty() }
                    .map { it.strength }.average()
                    .let { if (it.isNaN()) 0f else it.toFloat() },
                tierCounts = tiers.toList()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultStats())

    // Categories always derive from the full vault, not the filtered list,
    // so headers stay stable while searching.
    val categorizedPasswords: StateFlow<Map<String, List<PasswordEntity>>> =
        combine(passwords, allPasswords, _searchQuery) { filtered, full, query ->
            val source = if (query.isBlank()) full else filtered
            categorize(source)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun categorize(list: List<PasswordEntity>): Map<String, List<PasswordEntity>> {
        val critical = mutableListOf<PasswordEntity>()
        val weak = mutableListOf<PasswordEntity>()
        val mid = mutableListOf<PasswordEntity>()
        val strong = mutableListOf<PasswordEntity>()
        val elite = mutableListOf<PasswordEntity>()
        val incomplete = mutableListOf<PasswordEntity>()

        list.sortedBy { it.name.lowercase() }.forEach { password ->
            when {
                password.password.isEmpty() -> incomplete.add(password)
                // Breached credentials are critical regardless of entropy.
                (password.pwnedCount ?: 0) > 0 -> critical.add(password)
                password.strength <= 0 -> critical.add(password)
                password.strength == 1 -> weak.add(password)
                password.strength == 2 -> mid.add(password)
                password.strength == 3 -> strong.add(password)
                else -> elite.add(password)
            }
        }

        return linkedMapOf(
            "CRITICAL" to critical,
            "WEAK" to weak,
            "MID" to mid,
            "STRONG" to strong,
            "ELITE" to elite,
            "INCOMPLETE" to incomplete
        ).filter { it.value.isNotEmpty() }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }

    fun updateGeneratorSettings(settings: GeneratorSettings) {
        val coerced = settings.copy(
            length = settings.length.coerceIn(
                VaultPasswordEngine.MIN_LENGTH.toFloat(),
                VaultPasswordEngine.MAX_LENGTH.toFloat()
            )
        )
        _generatorSettings.value = coerced
        GeneratorSpecStore.save(appContext, specFromSettings(coerced))
    }

    companion object {
        fun settingsFromSpec(s: VaultPasswordEngine.PasswordSpec): GeneratorSettings =
            GeneratorSettings(
                length = s.length.toFloat(),
                includeLowercase = s.includeLowercase,
                includeSymbols = s.includeSymbols,
                includeNumbers = s.includeNumbers,
                includeUppercase = s.includeUppercase,
                excludeAmbiguous = s.excludeAmbiguous,
                customSymbols = s.customSymbols,
                pinMode = s.pinMode
            )
    }

    fun specFromSettings(s: GeneratorSettings = _generatorSettings.value): VaultPasswordEngine.PasswordSpec =
        VaultPasswordEngine.PasswordSpec(
            length = s.length.toInt(),
            includeLowercase = if (s.pinMode) false else s.includeLowercase,
            includeUppercase = if (s.pinMode) false else s.includeUppercase,
            includeNumbers = true,
            includeSymbols = if (s.pinMode) false else s.includeSymbols,
            customSymbols = if (s.pinMode) "" else s.customSymbols,
            excludeAmbiguous = s.excludeAmbiguous,
            pinMode = s.pinMode
        )

    fun addPassword(name: String, url: String?, user: String, pass: String) {
        val cleanName = name.trim()
        val cleanUser = user.trim()
        if (cleanName.isBlank() || cleanUser.isBlank()) return
        viewModelScope.launch {
            val pwnedCount = if (pass.isNotEmpty()) {
                when (val r = PwnedCheck.checkPwned(pass)) {
                    is PwnedCheck.PwnedResult.Checked -> r.count
                    is PwnedCheck.PwnedResult.Failed -> null
                }
            } else null
            val entity = PasswordEntity(
                name = cleanName,
                url = url?.trim()?.takeIf { it.isNotBlank() },
                username = cleanUser,
                password = pass,
                strength = if (pass.isNotEmpty()) PasswordGenerator.calculateStrength(pass) else 0,
                pwnedCount = pwnedCount,
                passwordHistory = emptyList()
            )
            runCatching { passwordDao.insertPassword(entity) }
        }
    }

    fun updatePassword(entity: PasswordEntity) {
        viewModelScope.launch {
            val oldEntity = passwordDao.getPasswordById(entity.id) ?: return@launch
            var newHistory = entity.passwordHistory
            if (oldEntity.password != entity.password) {
                newHistory = if (oldEntity.password.isNotEmpty()) {
                    (listOf(oldEntity.password) + oldEntity.passwordHistory)
                        .distinct()
                        .take(PasswordEntity.MAX_HISTORY)
                } else {
                    oldEntity.passwordHistory.take(PasswordEntity.MAX_HISTORY)
                }
            } else {
                newHistory = newHistory.take(PasswordEntity.MAX_HISTORY)
            }

            // Skip the network check when the secret did not change.
            val pwnedCount = if (entity.password.isEmpty()) {
                null
            } else if (oldEntity.password == entity.password) {
                oldEntity.pwnedCount
            } else {
                when (val r = PwnedCheck.checkPwned(entity.password)) {
                    is PwnedCheck.PwnedResult.Checked -> r.count
                    is PwnedCheck.PwnedResult.Failed -> oldEntity.pwnedCount
                }
            }
            val updatedEntity = entity.copy(
                name = entity.name.trim().takeIf { it.isNotBlank() } ?: oldEntity.name,
                username = entity.username.trim().takeIf { it.isNotBlank() } ?: oldEntity.username,
                url = entity.url?.trim()?.takeIf { it.isNotBlank() },
                strength = if (entity.password.isNotEmpty()) PasswordGenerator.calculateStrength(entity.password) else 0,
                pwnedCount = pwnedCount,
                passwordHistory = newHistory
            )
            passwordDao.updatePassword(updatedEntity)
        }
    }

    fun deletePassword(password: PasswordEntity) {
        viewModelScope.launch {
            passwordDao.deletePassword(password)
        }
    }

    fun checkPwned(password: PasswordEntity) {
        if (password.password.isEmpty()) return
        if (scanJob?.isActive == true) return
        viewModelScope.launch {
            when (val r = PwnedCheck.checkPwned(password.password)) {
                is PwnedCheck.PwnedResult.Checked -> passwordDao.updatePwnedCount(password.id, r.count)
                is PwnedCheck.PwnedResult.Failed -> Unit
            }
        }
    }

    /**
     * Manual breach scan. Auto-scan on launch was removed: it fired network
     * requests without consent and could 429. Callers invoke this explicitly.
     */
    fun scanVault() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0 to 0
            try {
                val current = passwordDao.getAllPasswords().first()
                    .filter { it.password.isNotEmpty() }
                var done = 0
                current.forEach { password ->
                    if (!scanJob?.isActive!!) return@forEach
                    when (val r = PwnedCheck.checkPwned(password.password)) {
                        is PwnedCheck.PwnedResult.Checked ->
                            passwordDao.updatePwnedCount(password.id, r.count)
                        is PwnedCheck.PwnedResult.Failed -> Unit
                    }
                    done++
                    _scanProgress.value = done to current.size
                    // Gentle pacing to respect HIBP rate limits.
                    kotlinx.coroutines.delay(350)
                }
            } finally {
                _isScanning.value = false
                _scanProgress.value = null
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _isScanning.value = false
        _scanProgress.value = null
    }

    fun importCsv(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            val result = CsvEngine.importCsvDetailed(context, uri)
            if (result.imported.isNotEmpty()) {
                val existing = passwordDao.getAllPasswordsSync()
                    .map { Triple(it.name.trim().lowercase(), it.username.trim().lowercase(), it.url?.trim()?.lowercase()) }
                    .toSet()
                val fresh = result.imported
                    .filterNot {
                        Triple(
                            it.name.trim().lowercase(),
                            it.username.trim().lowercase(),
                            it.url?.trim()?.lowercase()
                        ) in existing
                    }
                    .map { it.copy(id = 0) }
                if (fresh.isNotEmpty()) {
                    runCatching { passwordDao.insertPasswordsTx(fresh) }
                }
                _importMessage.value = ImportMessage(
                    imported = fresh.size,
                    skipped = result.skipped + (result.imported.size - fresh.size),
                    truncated = result.truncated
                )
            } else {
                _importMessage.value = ImportMessage(0, result.skipped, result.truncated)
            }
        }
    }

    suspend fun exportCsvText(): String {
        val all = passwordDao.getAllPasswordsSync()
        return CsvEngine.exportCsv(all)
    }

    /** Called once by the UI when entries with unknown breach status appear. */
    fun maybePromptScan(hasUnscanned: Boolean) {
        if (autoScanDone || hasUnscanned.not()) return
        autoScanDone = true
        // Intentionally no auto network call; UI shows the Scan button instead.
    }
}
