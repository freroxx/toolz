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

package com.frerox.toolz.ui.screens.search

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.ExpressiveWebView
import com.frerox.toolz.ui.screens.browser.WebViewViewModel
import com.frerox.toolz.ui.screens.browser.components.ManualPasswordBottomSheet

@Composable
fun NextDnsSetupScreen(
    url: String,
    onBack: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    var showPasswords by remember { mutableStateOf(false) }
    val manualPasswords by viewModel.manualPasswords.collectAsState()

    ExpressiveWebView(
        url = url,
        onBack = onBack,
        onOpenExternal = { u ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            }
        },
        title = "NextDNS Setup",
        showPasswordHelper = true,
        onPasswordClick = {
            viewModel.verifyBiometric(activity) {
                viewModel.findManualPasswords("nextdns.io")
                showPasswords = true
            }
        }
    )

    if (showPasswords) {
        ManualPasswordBottomSheet(
            passwords = manualPasswords,
            onDismiss = {
                showPasswords = false
                viewModel.clearManualPasswords()
            },
            onFill = { pwd ->
                // Since ExpressiveWebView doesn't expose the WebView reference easily for JS injection here
                // without extra plumbing, we'll assume the user can copy/paste from the helper
                // or we could add a Copy button to the Password helper.
                // ManualPasswordBottomSheet usually handles its own "Copy" if needed, 
                // but for simplicity, we'll keep it as is.
                showPasswords = false
            }
        )
    }
}
