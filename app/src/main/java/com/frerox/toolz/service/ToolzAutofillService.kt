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

package com.frerox.toolz.service

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.data.password.escapeLike
import com.frerox.toolz.util.password.PasswordUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class ToolzAutofillService : AutofillService() {

    @Inject
    lateinit var passwordDao: PasswordDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContext = request.fillContexts.lastOrNull()
        val structure = fillContext?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val parser = AssistStructureParser(structure)
        parser.parse()

        val domains = parser.domains
        val targetPackageName = runCatching { structure.activityComponent.packageName }.getOrNull()

        if (parser.usernameId == null && parser.passwordId == null) {
            callback.onSuccess(null)
            return
        }
        if (cancellationSignal.isCanceled) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            if (cancellationSignal.isCanceled) {
                callback.onSuccess(null)
                return@launch
            }
            val matches = findMatches(domains, targetPackageName)

            val responseBuilder = FillResponse.Builder()

            val authIntent = Intent(this@ToolzAutofillService, AutofillActivity::class.java).apply {
                putExtra("user_field_id", parser.usernameId)
                putExtra("pass_field_id", parser.passwordId)
                putExtra("domain", domains.firstOrNull())
                putExtra("package_name", targetPackageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Stable request code per target; immutable unless the OS needs mutability.
            val requestCode = (targetPackageName ?: domains.firstOrNull() ?: "vault").hashCode()
            val flags = PendingIntent.FLAG_CANCEL_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT)
            val pendingIntent = PendingIntent.getActivity(
                this@ToolzAutofillService,
                requestCode,
                authIntent,
                flags
            )

            // Direct matches are auth-gated too: no secret leaves the vault
            // without biometric/device-credential confirmation.
            matches.forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                val presentation = createPresentation(credential.name, credential.username)

                var hasValue = false
                parser.usernameId?.let {
                    datasetBuilder.setValue(it, AutofillValue.forText(credential.username), presentation)
                    hasValue = true
                }
                parser.passwordId?.let {
                    datasetBuilder.setValue(it, AutofillValue.forText(credential.password), presentation)
                    hasValue = true
                }

                if (hasValue) {
                    datasetBuilder.setAuthentication(pendingIntent.intentSender)
                    responseBuilder.addDataset(datasetBuilder.build())
                }
            }

            val masterDatasetBuilder = Dataset.Builder()
            val masterPresentation = createPresentation(
                title = if (matches.isNotEmpty()) "Search more in Vault" else "Open Toolz Vault",
                subtitle = "Unlock to select from all passwords"
            )

            val triggerId = parser.usernameId ?: parser.passwordId
            if (triggerId != null) {
                // Null value with auth presentation opens the vault picker.
                masterDatasetBuilder.setValue(triggerId, null, masterPresentation)
                masterDatasetBuilder.setAuthentication(pendingIntent.intentSender)
                responseBuilder.addDataset(masterDatasetBuilder.build())
            }

            if (cancellationSignal.isCanceled) {
                callback.onSuccess(null)
            } else {
                callback.onSuccess(responseBuilder.build())
            }
        }
    }

    private suspend fun findMatches(
        domains: List<String>,
        targetPackage: String?
    ): List<PasswordEntity> {
        val matches = mutableListOf<PasswordEntity>()
        domains.forEach { domain ->
            runCatching {
                matches.addAll(passwordDao.getPasswordsByDomain(domain.escapeLike()))
                matches.addAll(passwordDao.getPasswordsByExactHost(domain.escapeLike()))
                PasswordUtils.normalizeHost(domain)?.let {
                    matches.addAll(passwordDao.getByRegistrableDomain(it.escapeLike()))
                }
            }
        }
        targetPackage?.let { pkg ->
            if (pkg != packageName) {
                runCatching {
                    matches.addAll(passwordDao.getPasswordsByDomain(pkg.escapeLike()))
                    matches.addAll(passwordDao.searchPasswords(pkg.escapeLike()).take(5))
                }
            }
        }
        return matches.distinctBy { it.id }
            .sortedWith(
                compareByDescending<PasswordEntity> { isRelevant(it, domains, targetPackage) }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.name.lowercase() }
            )
            .take(5)
    }

    private fun isRelevant(
        entity: PasswordEntity,
        domains: List<String>,
        targetPackage: String?
    ): Int {
        val entityHost = PasswordUtils.normalizeHost(entity.url)?.lowercase()
        if (entityHost != null) {
            domains.forEach { d ->
                val want = PasswordUtils.normalizeHost(d)?.lowercase() ?: return@forEach
                if (entityHost == want) return 2
                if (entityHost.endsWith(".$want") || want.endsWith(".$entityHost")) return 1
            }
        }
        if (targetPackage != null && entity.url?.contains(targetPackage, ignoreCase = true) == true) return 1
        return 0
    }

    private fun createPresentation(title: String, subtitle: String): RemoteViews {
        return RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
            setImageViewResource(R.id.autofill_icon, android.R.drawable.ic_lock_lock)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Offer-to-save is intentionally out of scope: silently acknowledge
        // instead of dropping with an error, matching previous behavior.
        callback.onSuccess()
    }

    internal class AssistStructureParser(private val structure: AssistStructure) {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        val domains = mutableListOf<String>()

        fun parse() {
            for (i in 0 until structure.windowNodeCount) {
                traverse(structure.getWindowNodeAt(i).rootViewNode)
            }
        }

        private fun traverse(node: AssistStructure.ViewNode) {
            val hints = node.autofillHints?.map { it.lowercase() }.orEmpty()
            val hint = hints.firstOrNull() ?: ""
            val idEntry = node.idEntry?.lowercase() ?: ""
            val className = node.className?.lowercase() ?: ""
            val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
            val variation = node.inputType and android.text.InputType.TYPE_MASK_VARIATION

            val isPassword = variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                hints.any { it.contains("password") } ||
                className.contains("password") || idEntry.contains("password")

            if (isPassword) {
                if (passwordId == null) passwordId = node.autofillId
            } else if (usernameId == null && isUsernameHint(hint, hints, idEntry, contentDescription, node)) {
                usernameId = node.autofillId
            }

            node.webDomain?.let { if (!domains.contains(it)) domains.add(it) }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        private fun isUsernameHint(
            hint: String,
            hints: List<String>,
            idEntry: String,
            contentDescription: String,
            node: AssistStructure.ViewNode
        ): Boolean {
            if (hints.any { it.contains("username") || it.contains("email") }) return true
            if (hint.contains("username") || hint.contains("email") || hint.contains("login")) return true
            if (idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("login")) return true
            if (contentDescription.contains("username") || contentDescription.contains("email")) return true
            // Only treat a bare text field as username when it looks like a login form
            // (a password field was already seen or hints suggest identity).
            if (node.autofillType == View.AUTOFILL_TYPE_TEXT &&
                (passwordId != null || hints.any { it.contains("user") || it.contains("account") })
            ) return true
            return false
        }
    }
}
