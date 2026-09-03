package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CleanerBottomBar(selectedBytes:Long, cleanableBytes:Long, itemCount:Int, isAllSelected:Boolean, onClean:()->Unit, onToggleSelectAll:()->Unit, modifier:Modifier=Modifier){
    val context=LocalContext.current
    Surface(modifier=modifier.fillMaxWidth().navigationBarsPadding(), shape=RoundedCornerShape(20.dp), color=MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation=2.dp){
        Row(modifier=Modifier.padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically){
            Column(modifier=Modifier.weight(1f)){
                Text(if(selectedBytes==0L) "Nothing selected yet" else Formatter.formatFileSize(context,selectedBytes)+" selected", style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Medium, fontSize=13.sp))
                if (cleanableBytes>0) Text(if(selectedBytes==0L) "Tap a category to review, then pick what goes" else "$itemCount items • ${Formatter.formatFileSize(context,cleanableBytes)} total", style=MaterialTheme.typography.labelSmall.copy(fontSize=11.sp), color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick=onToggleSelectAll, contentPadding=PaddingValues(horizontal=10.dp)) { Text(if(isAllSelected) "Deselect" else "Select all", style=MaterialTheme.typography.labelSmall.copy(fontSize=12.sp)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick=onClean, enabled=selectedBytes>0, shape=RoundedCornerShape(20.dp), contentPadding=PaddingValues(horizontal=16.dp, vertical=10.dp), modifier=Modifier.height(40.dp)) {
                Text("Clean", style=MaterialTheme.typography.labelMedium.copy(fontWeight=FontWeight.Medium, fontSize=13.sp))
            }
        }
    }
}
