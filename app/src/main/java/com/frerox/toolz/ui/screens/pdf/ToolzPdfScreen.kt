package com.frerox.toolz.ui.screens.pdf

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBottomSheet by remember { mutableStateOf<PdfFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search PDF files...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    } else {
                        Text("PDF Tools")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }) {
                        Icon(if (isSearching) Icons.Default.MoreVert else Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.files.isEmpty() -> {
                    Text(
                        text = "No PDF files found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val filteredFiles = uiState.files.filter { 
                        it.name.contains(searchQuery, ignoreCase = true) 
                    }
                    PdfFileList(
                        files = filteredFiles,
                        onFileClick = { file ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(file.path)
                                type = "application/pdf"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open PDF"))
                        },
                        onMenuClick = { file ->
                            showBottomSheet = file
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFileOptionsBottomSheet(file: PdfFile, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(file.name, modifier = Modifier.padding(24.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            ListItem(headlineContent = { Text("Share File") }, leadingContent = { Icon(Icons.Default.Share, null) }, modifier = Modifier.clickable { onDismiss() })
            ListItem(headlineContent = { Text("Rename") }, leadingContent = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.clickable { onDismiss() })
            ListItem(headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { onDismiss() })
        }
    }
}
