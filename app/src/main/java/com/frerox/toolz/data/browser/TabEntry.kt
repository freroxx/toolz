package com.frerox.toolz.data.browser

import java.util.UUID

data class TabEntry(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "New Tab",
    val faviconUrl: String? = null,
    val previewPath: String? = null,
    val isDesktopMode: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
