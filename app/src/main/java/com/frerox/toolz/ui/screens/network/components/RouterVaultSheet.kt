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

package com.frerox.toolz.ui.screens.network.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.network.RouterCredential

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterVaultSheet(
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val credentials = remember {
        listOf(
            RouterCredential("TP-Link", "admin", "admin", "Many models"),
            RouterCredential("TP-Link", "admin", "password", "WR841N"),
            RouterCredential("TP-Link", "admin", "admin123", "Archer series"),
            RouterCredential("Huawei", "admin", "admin", "HG series"),
            RouterCredential("Huawei", "telecomadmin", "admintelecom", "Fiber models"),
            RouterCredential("Huawei", "root", "admin", "B series"),
            RouterCredential("D-Link", "admin", "(blank)", "DIR series"),
            RouterCredential("D-Link", "Admin", "Admin", "DSL series"),
            RouterCredential("Nokia", "admin", "admin", "G-240W-A"),
            RouterCredential("Nokia", "root", "admin", "ONT series"),
            RouterCredential("Nokia", "user", "user", "Beacon series")
        )
    }

    val filtered = credentials.filter { 
        it.brand.contains(searchQuery, ignoreCase = true) || 
        it.model.contains(searchQuery, ignoreCase = true) 
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Router Password Vault",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search brand or model...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { cred ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cred.brand, style = MaterialTheme.typography.titleMedium)
                                Text(cred.model, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("User: ${cred.username}", style = MaterialTheme.typography.bodyMedium)
                            Text("Pass: ${cred.password}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
