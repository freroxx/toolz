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
