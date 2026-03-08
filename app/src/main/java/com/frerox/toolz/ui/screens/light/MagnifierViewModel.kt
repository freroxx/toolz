package com.frerox.toolz.ui.screens.light

import androidx.lifecycle.ViewModel
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MagnifierViewModel @Inject constructor(
    val repository: SettingsRepository
) : ViewModel()
