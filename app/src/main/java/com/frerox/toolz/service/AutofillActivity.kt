package com.frerox.toolz.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.util.security.BiometricPromptUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutofillActivity : AppCompatActivity() {

    @Inject
    lateinit var passwordDao: PasswordDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userFieldId = intent.getParcelableExtra<AutofillId>("user_field_id")
        val passFieldId = intent.getParcelableExtra<AutofillId>("pass_field_id")
        val domain = intent.getStringExtra("domain")
        val targetPackage = intent.getStringExtra("package_name")

        BiometricPromptUtils.showBiometricPrompt(
            activity = this,
            onSuccess = {
                setContent {
                    ToolzTheme {
                        AutofillSelectionScreen(
                            domain = domain,
                            packageName = targetPackage,
                            onSelected = { credential ->
                                returnResult(credential, userFieldId, passFieldId)
                            },
                            onDismiss = { finish() }
                        )
                    }
                }
            },
            onError = { _, _ -> finish() },
            onFailed = { finish() }
        )
    }

    private fun returnResult(credential: PasswordEntity, userFieldId: AutofillId?, passFieldId: AutofillId?) {
        val datasetBuilder = Dataset.Builder()
        
        // Final presentation is sometimes needed for confirmation or re-selection
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, credential.name)
            setTextViewText(R.id.autofill_subtitle, credential.username)
        }

        var added = false
        userFieldId?.let { 
            datasetBuilder.setValue(it, AutofillValue.forText(credential.username), presentation)
            added = true
        }
        passFieldId?.let { 
            datasetBuilder.setValue(it, AutofillValue.forText(credential.password), presentation)
            added = true
        }

        if (added) {
            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
            }
            setResult(Activity.RESULT_OK, replyIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AutofillSelectionScreen(
        domain: String?,
        packageName: String?,
        onSelected: (PasswordEntity) -> Unit,
        onDismiss: () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        var searchResults by remember { mutableStateOf<List<PasswordEntity>>(emptyList()) }
        var query by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val initial = mutableListOf<PasswordEntity>()
            if (!domain.isNullOrBlank()) initial.addAll(passwordDao.getPasswordsByDomain(domain))
            if (initial.isEmpty() && !packageName.isNullOrBlank()) initial.addAll(passwordDao.getPasswordsByDomain(packageName))
            if (initial.isEmpty()) initial.addAll(passwordDao.getAllPasswords().first().take(15))
            searchResults = initial.distinctBy { it.id }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Vault Autofill",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        scope.launch {
                            searchResults = if (it.isBlank()) {
                                val initial = mutableListOf<PasswordEntity>()
                                if (!domain.isNullOrBlank()) initial.addAll(passwordDao.getPasswordsByDomain(domain))
                                if (initial.isEmpty() && !packageName.isNullOrBlank()) initial.addAll(passwordDao.getPasswordsByDomain(packageName))
                                if (initial.isEmpty()) initial.addAll(passwordDao.getAllPasswords().first().take(15))
                                initial.distinctBy { it.id }
                            } else {
                                passwordDao.searchPasswords(it)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by app or username...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(min = 200.dp, max = 400.dp)
                ) {
                    items(searchResults) { credential ->
                        Surface(
                            onClick = { onSelected(credential) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        credential.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        credential.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        credential.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    if (searchResults.isEmpty()) {
                        item {
                            Text(
                                "No credentials found",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
