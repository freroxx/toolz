package com.frerox.toolz.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconState = produceState<Drawable?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (_: Exception) { null }
        }
    }
    if (iconState.value != null) {
        Image(
            painter = rememberAsyncImagePainter(iconState.value),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Android, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        }
    }
}
