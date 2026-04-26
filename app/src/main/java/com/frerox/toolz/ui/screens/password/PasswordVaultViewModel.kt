package com.frerox.toolz.ui.screens.password

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.util.password.CsvEngine
import com.frerox.toolz.util.password.PasswordGenerator
import com.frerox.toolz.util.password.PwnedCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordVaultViewModel @Inject constructor(
    private val passwordDao: PasswordDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _generatorSettings = MutableStateFlow(GeneratorSettings())
    val generatorSettings = _generatorSettings.asStateFlow()

    data class GeneratorSettings(
        val length: Float = 16f,
        val includeSymbols: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeUppercase: Boolean = true
    )

    data class VaultStats(
        val total: Int = 0,
        val breached: Int = 0,
        val weak: Int = 0,
        val averageStrength: Float = 0f
    )

    val passwords = _searchQuery
        .combine(passwordDao.getAllPasswords()) { query, list ->
            if (query.isBlank()) list
            else list.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.username.contains(query, ignoreCase = true) ||
                (it.password.isEmpty() && "no password".contains(query, ignoreCase = true))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultStats = passwordDao.getAllPasswords().map { list ->
        if (list.isEmpty()) VaultStats()
        else {
            VaultStats(
                total = list.size,
                breached = list.count { it.pwnedCount != null && it.pwnedCount > 0 },
                weak = list.count { it.password.isNotEmpty() && it.strength < 3 },
                averageStrength = list.filter { it.password.isNotEmpty() }.map { it.strength }.average().let { if (it.isNaN()) 0f else it.toFloat() }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultStats())

    val categorizedPasswords = passwords.map { list ->
        val mustChange = mutableListOf<PasswordEntity>()
        val weak = mutableListOf<PasswordEntity>()
        val safe = mutableListOf<PasswordEntity>()
        val incomplete = mutableListOf<PasswordEntity>()

        list.forEach { password ->
            if (password.password.isEmpty()) {
                incomplete.add(password)
            } else if (password.pwnedCount != null && password.pwnedCount > 0) {
                mustChange.add(password)
            } else if (password.strength < 3) {
                weak.add(password)
            } else {
                safe.add(password)
            }
        }
        
        mapOf(
            "INCOMPLETE" to incomplete,
            "MUST CHANGE" to mustChange,
            "WEAK" to weak,
            "SAFE" to safe
        ).filter { it.value.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            passwordDao.getAllPasswords().first().let { list ->
                if (list.isNotEmpty() && list.any { it.pwnedCount == null && it.password.isNotEmpty() }) {
                    scanVault()
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun updateGeneratorSettings(settings: GeneratorSettings) {
        _generatorSettings.value = settings
    }

    fun addPassword(name: String, url: String?, user: String, pass: String) {
        viewModelScope.launch {
            val pwnedCount = if (pass.isNotEmpty()) PwnedCheck.isPwned(pass) else null
            val entity = PasswordEntity(
                name = name,
                url = url,
                username = user,
                password = pass,
                strength = if (pass.isNotEmpty()) PasswordGenerator.calculateStrength(pass) else 0,
                pwnedCount = pwnedCount
            )
            passwordDao.insertPassword(entity)
        }
    }

    fun updatePassword(entity: PasswordEntity) {
        viewModelScope.launch {
            val oldEntity = passwordDao.getPasswordById(entity.id)
            var newHistory = entity.passwordHistory
            if (oldEntity != null && oldEntity.password != entity.password) {
                newHistory = (listOf(oldEntity.password) + oldEntity.passwordHistory).take(10)
            }

            val pwnedCount = if (entity.password.isNotEmpty()) PwnedCheck.isPwned(entity.password) else null
            val updatedEntity = entity.copy(
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
        viewModelScope.launch {
            val count = PwnedCheck.isPwned(password.password)
            passwordDao.updatePwnedCount(password.id, count)
        }
    }

    fun scanVault() {
        viewModelScope.launch {
            _isScanning.value = true
            val currentPasswords = passwordDao.getAllPasswords().first()
            currentPasswords.forEach { password ->
                if (password.password.isNotEmpty()) {
                    val count = PwnedCheck.isPwned(password.password)
                    passwordDao.updatePwnedCount(password.id, count)
                }
            }
            _isScanning.value = false
        }
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val imported = CsvEngine.importCsv(context, uri)
            imported.forEach { passwordDao.insertPassword(it) }
            scanVault()
        }
    }
}
