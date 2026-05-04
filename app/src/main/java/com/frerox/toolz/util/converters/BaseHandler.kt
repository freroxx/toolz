package com.frerox.toolz.util.converters

import android.net.Uri
import com.frerox.toolz.util.ConversionEngine
import kotlinx.coroutines.flow.Flow

interface ConversionHandler {
    fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean
    ): Flow<ConversionEngine.ConversionStatus>
}
