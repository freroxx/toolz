package com.frerox.toolz.ui.screens.qr

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.service.CaffeinateService
import com.frerox.toolz.ToolzApplication
import com.frerox.toolz.util.QREngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

enum class QRInputType(val displayName: String, val icon: ImageVector) {
    TEXT("Text", Icons.Rounded.Article),
    URL("URL / Link", Icons.Rounded.Language),
    EMAIL("Email Address", Icons.Rounded.Email),
    PHONE("Phone Number", Icons.Rounded.Phone),
    WIFI("Wi-Fi Network", Icons.Rounded.Wifi),
    SMS("SMS / Message", Icons.Rounded.Sms),
    ENCRYPTED("Encrypted", Icons.Rounded.Lock)
}

@OptIn(FlowPreview::class)
@HiltViewModel
class QRViewModel @Inject constructor() : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _noteText = MutableStateFlow("")
    val noteText = _noteText.asStateFlow()
    
    private val _noteSize = MutableStateFlow(16f)
    val noteSize = _noteSize.asStateFlow()
    
    private val _notePosition = MutableStateFlow("BOTTOM")
    val notePosition = _notePosition.asStateFlow()
    
    private val _isNoteEnabled = MutableStateFlow(false)
    val isNoteEnabled = _isNoteEnabled.asStateFlow()

    // Styling States
    private val _foregroundColor = MutableStateFlow<QREngine.QrColor>(QREngine.QrColor.Solid(Color.BLACK))
    val foregroundColor = _foregroundColor.asStateFlow()

    private val _backgroundColor = MutableStateFlow<QREngine.QrColor>(QREngine.QrColor.Solid(Color.WHITE))
    val backgroundColor = _backgroundColor.asStateFlow()

    private val _dotShape = MutableStateFlow(QREngine.DotShape.SQUARE)
    val dotShape = _dotShape.asStateFlow()

    private val _eyeShape = MutableStateFlow(QREngine.EyeShape.SQUARE)
    val eyeShape = _eyeShape.asStateFlow()

    private val _quietZone = MutableStateFlow(1)
    val quietZone = _quietZone.asStateFlow()

    private val _logoClearance = MutableStateFlow(0.22f)
    val logoClearance = _logoClearance.asStateFlow()

    // Logo States
    private val _logoUri = MutableStateFlow<Uri?>(null)
    val logoUri = _logoUri.asStateFlow()

    private val _logoBitmap = MutableStateFlow<Bitmap?>(null)
    val logoBitmap = _logoBitmap.asStateFlow()

    // Clipboard Suggestion
    private val _clipboardSuggestion = MutableStateFlow<Pair<String, QRInputType>?>(null)
    val clipboardSuggestion = _clipboardSuggestion.asStateFlow()

    // Contrast Warning
    val contrastRatio: StateFlow<Double> = combine(_foregroundColor, _backgroundColor) { fg, bg ->
        calculateContrast(fg.getPrimaryColor(), bg.getPrimaryColor())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21.0)

    val isContrastSafe: StateFlow<Boolean> = contrastRatio
        .map { it >= 3.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Dynamic Color Settings
    private val _isInheritingTheme = MutableStateFlow(true)
    val isInheritingTheme = _isInheritingTheme.asStateFlow()

    private var appPrimaryColorValue: Int? = null
    private var appSecondaryColorValue: Int? = null
    private var appSurfaceColorValue: Int? = null

    // Regex Categorizer
    val detectedType: StateFlow<QRInputType> = _inputText
        .map { text -> detectInputType(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QRInputType.TEXT)

    // Debounced and optimized QR Bitmap StateFlow
    val qrBitmap: StateFlow<Bitmap?> = combine(
        _inputText.debounce(300),
        _foregroundColor,
        _backgroundColor,
        _dotShape,
        _eyeShape,
        _logoBitmap,
        _quietZone,
        _logoClearance,
        _isInheritingTheme,
        _noteText,
        _noteSize,
        _notePosition,
        _isNoteEnabled
    ) { args ->
        val text = args[0] as String
        val fg = args[1] as QREngine.QrColor
        val bg = args[2] as QREngine.QrColor
        val dot = args[3] as QREngine.DotShape
        val eye = args[4] as QREngine.EyeShape
        val logo = args[5] as Bitmap?
        val qz = args[6] as Int
        val lc = args[7] as Float
        val inherit = args[8] as Boolean
        val noteText = args[9] as String
        val noteSize = args[10] as Float
        val notePosition = args[11] as String
        val isNoteEnabled = args[12] as Boolean

        if (text.isBlank()) return@combine null

        val finalFg = if (inherit && appPrimaryColorValue != null && appSecondaryColorValue != null) {
            QREngine.QrColor.LinearGradient(listOf(appPrimaryColorValue!!, appSecondaryColorValue!!))
        } else fg
        
        val finalBg = if (inherit && appSurfaceColorValue != null) {
            QREngine.QrColor.Solid(appSurfaceColorValue!!)
        } else bg
        
        QREngine.generate(
            text = text,
            size = 1024,
            foregroundColor = finalFg,
            backgroundColor = finalBg,
            dotShape = dot,
            eyeShape = eye,
            logoBitmap = logo,
            quietZone = qz,
            logoClearance = lc,
            noteText = noteText,
            noteSize = noteSize,
            notePosition = notePosition,
            isNoteEnabled = isNoteEnabled
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun updateNoteText(text: String) {
        _noteText.value = text
    }
    
    fun updateNoteSize(size: Float) {
        _noteSize.value = size
    }
    
    fun updateNotePosition(position: String) {
        _notePosition.value = position
    }
    
    fun toggleNoteEnabled(enabled: Boolean) {
        _isNoteEnabled.value = enabled
    }

    fun updateForegroundColor(color: QREngine.QrColor) {
        _isInheritingTheme.value = false
        _foregroundColor.value = color
    }

    fun updateBackgroundColor(color: QREngine.QrColor) {
        _isInheritingTheme.value = false
        _backgroundColor.value = color
    }

    fun updateDotShape(shape: QREngine.DotShape) {
        _dotShape.value = shape
    }

    fun updateEyeShape(shape: QREngine.EyeShape) {
        _eyeShape.value = shape
    }

    fun updateQuietZone(value: Int) {
        _quietZone.value = value
    }

    fun updateLogoClearance(value: Float) {
        _logoClearance.value = value
    }

    fun toggleInheritTheme(inherit: Boolean) {
        _isInheritingTheme.value = inherit
    }

    fun updateThemeColors(primary: Int, secondary: Int, surface: Int) {
        appPrimaryColorValue = primary
        appSecondaryColorValue = secondary
        appSurfaceColorValue = surface
    }

    fun checkClipboard(context: Context) {
        viewModelScope.launch {
            // Priority 1: Shizuku (if we have the executor injected or helper available)
            // Note: QRViewModel doesn't currently have shizukuExecutor injected. 
            // We can check ShizukuHelper but without the executor we can't read.
            // However, we can at least be safer with the focus check.
            
            if (!ToolzApplication.isFocused.value) return@launch
            
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotBlank() && text != _inputText.value) {
                        val type = detectInputType(text)
                        if (type != QRInputType.TEXT || text.length > 5) {
                            _clipboardSuggestion.value = text to type
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore denial errors
            }
        }
    }

    fun clearClipboardSuggestion() {
        _clipboardSuggestion.value = null
    }

    fun setLogoUri(uri: Uri?, context: Context) {
        _logoUri.value = uri
        if (uri == null) {
            _logoBitmap.value = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    _logoBitmap.value = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _logoBitmap.value = null
            }
        }
    }

    private fun detectInputType(text: String): QRInputType {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return QRInputType.TEXT

        val urlRegex = Regex("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$", RegexOption.IGNORE_CASE)
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val phoneRegex = Regex("^(\\+?[0-9\\s\\-()]{7,18})$")
        val wifiRegex = Regex("^(WIFI|wifi):S:[^;]+;T:[^;]+;P:[^;]*;;$", RegexOption.IGNORE_CASE)
        val encryptedRegex = Regex("^[A-Za-z0-9+/=]{20,}$") // Heuristic for base64 encrypted text

        return when {
            trimmed.startsWith("http", ignoreCase = true) || urlRegex.matches(trimmed) -> QRInputType.URL
            trimmed.startsWith("mailto:", ignoreCase = true) || emailRegex.matches(trimmed) -> QRInputType.EMAIL
            trimmed.startsWith("tel:", ignoreCase = true) || phoneRegex.matches(trimmed) -> QRInputType.PHONE
            trimmed.startsWith("wifi:", ignoreCase = true) || wifiRegex.matches(trimmed) -> QRInputType.WIFI
            encryptedRegex.matches(trimmed) -> QRInputType.ENCRYPTED
            else -> QRInputType.TEXT
        }
    }

    private fun calculateContrast(color1: Int, color2: Int): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
    }

    private fun calculateLuminance(color: Int): Double {
        var r = Color.red(color) / 255.0
        var g = Color.green(color) / 255.0
        var b = Color.blue(color) / 255.0

        r = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        g = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        b = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun toggleCaffeinate(context: Context, enable: Boolean) {
        val intent = Intent(context, CaffeinateService::class.java).apply {
            action = if (enable) CaffeinateService.ACTION_START else CaffeinateService.ACTION_STOP
            if (enable) {
                putExtra(CaffeinateService.EXTRA_INFINITE, true)
                putExtra(CaffeinateService.EXTRA_COLOR, appPrimaryColorValue ?: Color.BLUE)
            }
        }
        context.startService(intent)
    }

    fun saveQrCode(context: Context, bitmap: Bitmap, onSuccess: (Uri) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = "QR_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Toolz")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry")

                resolver.openOutputStream(uri).use { it?.let { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) { onSuccess(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun exportSvg(context: Context, text: String, onSuccess: (Uri) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fg = _foregroundColor.value.getPrimaryColor()
                val bg = _backgroundColor.value.getPrimaryColor()
                val svgString = QREngine.generateSvg(text, fg, bg, _dotShape.value, _eyeShape.value, _quietZone.value)
                
                val filename = "QR_${System.currentTimeMillis()}.svg"
                val file = File(context.cacheDir, filename)
                file.writeText(svgString)

                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                withContext(Dispatchers.Main) { onSuccess(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun shareQrCode(context: Context, bitmap: Bitmap, onError: (Throwable) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "shared_qr.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share QR Code"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }
}
