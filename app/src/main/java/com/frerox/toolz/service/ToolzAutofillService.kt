package com.frerox.toolz.service

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class ToolzAutofillService : AutofillService() {

    @Inject
    lateinit var passwordDao: PasswordDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContext = request.fillContexts.last()
        val structure = fillContext.structure
        val parser = AssistStructureParser(structure)
        parser.parse()

        val domain = parser.domain
        val targetPackageName = structure.activityComponent.packageName

        Log.d("ToolzAutofill", "onFillRequest: domain=$domain, package=$targetPackageName")

        if (parser.usernameId == null && parser.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            val matches = mutableListOf<PasswordEntity>()
            
            // Try matching by domain or package name
            domain?.let { matches.addAll(passwordDao.getPasswordsByDomain(it)) }
            if (targetPackageName != null && targetPackageName != packageName) {
                matches.addAll(passwordDao.getPasswordsByDomain(targetPackageName))
            }

            val responseBuilder = FillResponse.Builder()
            
            val authIntent = Intent(this@ToolzAutofillService, AutofillActivity::class.java).apply {
                putExtra("user_field_id", parser.usernameId)
                putExtra("pass_field_id", parser.passwordId)
                putExtra("domain", domain)
                putExtra("package_name", targetPackageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this@ToolzAutofillService,
                System.currentTimeMillis().toInt(),
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // 1. Add direct matches as datasets
            matches.distinctBy { it.id }.take(3).forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                val presentation = createPresentation(credential.name, credential.username)
                
                var hasValue = false
                // Always set BOTH username and password if we found them, 
                // so the system knows what to fill in BOTH fields.
                parser.usernameId?.let {
                    datasetBuilder.setValue(it, AutofillValue.forText(credential.username), presentation)
                    hasValue = true
                }
                parser.passwordId?.let {
                    datasetBuilder.setValue(it, AutofillValue.forText(credential.password), presentation)
                    hasValue = true
                }
                
                if (hasValue) {
                    responseBuilder.addDataset(datasetBuilder.build())
                }
            }

            // 2. Add an entry to open the full Vault search
            val masterDatasetBuilder = Dataset.Builder()
            val masterPresentation = createPresentation(
                title = if (matches.isNotEmpty()) "Search more in Vault" else "Open Toolz Vault",
                subtitle = "Click to select from all passwords"
            )

            val triggerId = parser.usernameId ?: parser.passwordId
            if (triggerId != null) {
                masterDatasetBuilder.setValue(triggerId, null, masterPresentation)
                masterDatasetBuilder.setAuthentication(pendingIntent.intentSender)
                responseBuilder.addDataset(masterDatasetBuilder.build())
            }

            callback.onSuccess(responseBuilder.build())
        }
    }

    private fun createPresentation(title: String, subtitle: String): RemoteViews {
        return RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
            setImageViewResource(R.id.autofill_icon, android.R.drawable.ic_lock_lock)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private class AssistStructureParser(private val structure: AssistStructure) {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var domain: String? = null

        fun parse() {
            for (i in 0 until structure.windowNodeCount) {
                traverse(structure.getWindowNodeAt(i).rootViewNode)
            }
        }

        private fun traverse(node: AssistStructure.ViewNode) {
            val hint = node.autofillHints?.firstOrNull()?.lowercase() ?: ""
            val idEntry = node.idEntry?.lowercase() ?: ""
            val className = node.className?.lowercase() ?: ""
            val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
            
            val isPassword = (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                             (node.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                             (node.inputType and android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0 ||
                             className.contains("password") || idEntry.contains("password") || hint.contains("password")

            if (isPassword) {
                if (passwordId == null) passwordId = node.autofillId
            } else if (usernameId == null && (
                hint.contains("username") || hint.contains("email") || hint.contains("login") ||
                idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("user") || 
                idEntry.contains("login") || contentDescription.contains("username") || 
                contentDescription.contains("email") || node.autofillType == View.AUTOFILL_TYPE_TEXT
            )) {
                usernameId = node.autofillId
            }

            if (domain == null && node.webDomain != null) {
                domain = node.webDomain
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }
    }
}
