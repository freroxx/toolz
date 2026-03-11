package com.frerox.toolz.ui.screens.pdf

import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.databinding.LayoutPdfViewerBinding
import com.frerox.toolz.ui.components.bouncyClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val isNightMode by viewModel.isNightMode.collectAsStateWithLifecycle()

    var showBottomSheet by remember { mutableStateOf<PdfFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { 
                            if (uiState is PdfViewModel.PdfUiState.Viewer) viewModel.closeViewer() 
                            else onNavigateBack() 
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    
                    Text(
                        text = if (uiState is PdfViewModel.PdfUiState.Viewer) "READER" else "PDF TOOLS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    IconButton(
                        onClick = { viewModel.toggleNightMode() },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            if (isNightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, 
                            contentDescription = "Theme"
                        )
                    }
                }

                if (uiState !is PdfViewModel.PdfUiState.Viewer) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your documents...") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is PdfViewModel.PdfUiState.Viewer -> {
                    PdfReaderView(uri = state.uri)
                }
                else -> {
                    PdfListContent(
                        pdfFiles = pdfFiles,
                        searchQuery = searchQuery,
                        onFileClick = { file -> viewModel.openPdf(file.uri) },
                        onMenuClick = { file -> showBottomSheet = file }
                    )
                }
            }
        }
    }

    showBottomSheet?.let { file ->
        PdfFileOptionsBottomSheet(
            file = file,
            onDismiss = { showBottomSheet = null }
        )
    }
}

@Composable
fun PdfListContent(
    pdfFiles: List<PdfFile>,
    searchQuery: String,
    onFileClick: (PdfFile) -> Unit,
    onMenuClick: (PdfFile) -> Unit
) {
    if (pdfFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No PDF files found", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        }
    } else {
        val filteredFiles = pdfFiles.filter { 
            it.name.contains(searchQuery, ignoreCase = true) 
        }
        PdfFileList(
            files = filteredFiles,
            onFileClick = onFileClick,
            onMenuClick = onMenuClick
        )
    }
}

@Composable
fun PdfReaderView(uri: Uri) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity ?: return
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
        AndroidViewBinding(LayoutPdfViewerBinding::inflate) {
            val fragmentManager = fragmentActivity.supportFragmentManager
            var pdfFragment = fragmentManager.findFragmentByTag("pdf_viewer") as? PdfViewerFragment
            
            if (pdfFragment == null) {
                pdfFragment = PdfViewerFragment()
                fragmentManager.beginTransaction()
                    .replace(fragmentContainerView.id, pdfFragment, "pdf_viewer")
                    .commitNow()
            }
            
            pdfFragment.documentUri = uri
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Integrated PDF viewer requires Android 12+ with SDK extension 13")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFileOptionsBottomSheet(file: PdfFile, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = file.name, 
                fontWeight = FontWeight.Black, 
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            val options = listOf(
                Triple("Share Document", Icons.Rounded.Share, {}),
                Triple("Rename File", Icons.Rounded.Edit, {}),
                Triple("Details", Icons.Rounded.Info, {}),
                Triple("Delete", Icons.Rounded.Delete, { })
            )
            
            options.forEach { (text, icon, action) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).bouncyClick { action(); onDismiss() },
                    color = if (text == "Delete") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon, 
                            null, 
                            tint = if (text == "Delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text, 
                            fontWeight = FontWeight.Bold,
                            color = if (text == "Delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
