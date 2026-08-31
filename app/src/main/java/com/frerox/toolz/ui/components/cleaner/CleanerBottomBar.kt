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

package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton

@Composable fun CleanerBottomBar(selectedBytes:Long, cleanableBytes:Long, itemCount:Int, isAllSelected:Boolean, onClean:()->Unit, onToggleSelectAll:()->Unit, modifier:Modifier=Modifier){
    val context=LocalContext.current
    Surface(modifier=modifier.fillMaxWidth().navigationBarsPadding(), shape=CircleShape, color=MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation=4.dp, shadowElevation=8.dp){
        Row(modifier=Modifier.padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically){
            Column(modifier=Modifier.weight(1f)){
                Text(if(selectedBytes==0L) "No selection" else Formatter.formatFileSize(context,selectedBytes)+" selected", style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.Black)
                if(selectedBytes < cleanableBytes && selectedBytes>0) Text("${Formatter.formatFileSize(context,cleanableBytes)} total • $itemCount items", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) else if(cleanableBytes>0) Text("$itemCount items", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            ToolzExpressiveIconButton(onClick=onToggleSelectAll, modifier=Modifier.size(48.dp)){ Icon(if(isAllSelected) Icons.Rounded.DoneAll else Icons.Rounded.SelectAll, null, Modifier.size(20.dp))}
            Spacer(Modifier.width(8.dp))
            Button(onClick=onClean, enabled=selectedBytes>0, shape=CircleShape, contentPadding=PaddingValues(horizontal=20.dp, vertical=12.dp)){
                Icon(Icons.Rounded.AutoDelete,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Clean", fontWeight=FontWeight.Black)
            }
        }
    }
}
