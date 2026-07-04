package com.frerox.toolz.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.util.shizuku.ShizukuHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuSetupBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isInstalled = ShizukuHelper.isAvailable()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ExtraLargeExpressiveShape
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "SHIZUKU SETUP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            
            Spacer(Modifier.height(24.dp))

            if (!isInstalled) {
                ShizukuNotInstalledView(context)
            } else {
                ShizukuNotRunningView(context)
            }

            Spacer(Modifier.height(32.dp))
            
            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = BouncyShape
            ) {
                Text("GOT IT")
            }
        }
    }
}

@Composable
private fun ShizukuNotInstalledView(context: Context) {
    Column {
        Text(
            "Shizuku is not installed on this device. It is required for advanced background features.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(Modifier.height(24.dp))
        
        ToolzExpressiveButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MediumExpressiveShape
        ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
            Spacer(Modifier.width(12.dp))
            Text("GET FROM PLAY STORE")
        }
        
        Spacer(Modifier.height(12.dp))
        
        ToolzOutlinedExpressiveButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MediumExpressiveShape
        ) {
            Text("DOWNLOAD APK")
        }
    }
}

@Composable
private fun ShizukuNotRunningView(context: Context) {
    Column {
        Text(
            "Shizuku is installed but the service is not running. Please follow these steps:",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(Modifier.height(24.dp))
        
        val steps = listOf(
            "Open Shizuku app" to Icons.Rounded.Launch,
            "Enable Wireless Debugging in Android Developer Options" to Icons.Rounded.SettingsEthernet,
            "Pair device (if first time)" to Icons.Rounded.Link,
            "Tap 'Start' in Shizuku app" to Icons.Rounded.PlayArrow
        )
        
        steps.forEachIndexed { index, (text, icon) ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        ToolzOutlinedExpressiveButton(
            onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MediumExpressiveShape
        ) {
            Text("OPEN SHIZUKU")
        }
    }
}
